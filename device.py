"""Set and clear an iPhone's Developer Mode location over a trusted USB connection."""

import asyncio
import atexit
from concurrent.futures import TimeoutError as FutureTimeout
from contextlib import AsyncExitStack, suppress
from dataclasses import dataclass
import importlib.metadata
import math
import os
from pathlib import Path
import re
import tempfile
import threading

PMD_VERSION = "11.63.1"
IS_WINDOWS = os.name == "nt"
USBMUX_SOCKET = "127.0.0.1:27015" if IS_WINDOWS else "/var/run/usbmuxd"
_session_dir = Path(os.environ.get("HANGZHOU_LOCATION_SESSION_DIR", ""))
_owned_temp = None if _session_dir.is_absolute() else tempfile.TemporaryDirectory(prefix="hangzhou-location-")
PAIRING_CACHE = (_session_dir if _owned_temp is None else Path(_owned_temp.name)) / "pairing"
DEVELOPER_MODE_HELP = "请在 iPhone 的「设置 → 隐私与安全性 → 开发者模式」中开启，重启并确认后重试。"
RESTORE_HELP = "请保持 USB 连接，解锁 iPhone 后重试「恢复真实定位」。也可重启 iPhone，再打开系统「地图」确认定位。"


class _UserError(Exception):
    """An error safe to show without exposing a device identifier."""


@dataclass(frozen=True)
class _Phone:
    serial: str
    version: str
    major: int


def _dependencies():
    try:
        version = importlib.metadata.version("pymobiledevice3")
    except importlib.metadata.PackageNotFoundError:
        version = ""
    if version != PMD_VERSION:
        raise _UserError(f"USB 定位组件未就绪。请通过本工具的启动文件重新启动，安装版本 {PMD_VERSION}。")


async def _lockdown(serial):
    from pymobiledevice3.lockdown import create_using_usbmux

    client = await create_using_usbmux(
        serial=serial,
        autopair=False,
        connection_type="USB",
        pairing_records_cache_folder=PAIRING_CACHE,
        usbmux_address=USBMUX_SOCKET,
    )
    if not client.paired:
        await client.close()
        pairing_app = "Apple 设备 App 或 iTunes" if IS_WINDOWS else "访达"
        raise _UserError(f"请解锁 iPhone，在{pairing_app}中选择这部 iPhone，完成「信任此电脑」后重试。")
    return client


async def _phone():
    from pymobiledevice3 import usbmux
    from pymobiledevice3.exceptions import MessageNotSupportedError
    from pymobiledevice3.services.mobile_image_mounter import MobileImageMounterService

    devices = await usbmux.select_devices_by_connection_type("USB", usbmux_address=USBMUX_SOCKET)
    devices = {device.serial for device in devices}
    if len(devices) != 1:
        if devices:
            raise _UserError("检测到多部 USB 设备。请只保留要设置的那部 iPhone，再重试。")
        raise _UserError("未找到 USB 连接的 iPhone。请用支持数据传输的线连接，并解锁手机。")

    serial = devices.pop()
    async with await _lockdown(serial) as client:
        if client.all_values.get("DeviceClass") != "iPhone":
            raise _UserError("已连接的 USB 设备不是 iPhone。请连接要设置的 iPhone。")
        version = client.product_version
        version_parts = re.match(r"^(\d+)\.(\d+)(?:\.\d+)?", version or "")
        if not version_parts or tuple(map(int, version_parts.groups())) < (17, 4):
            raise _UserError("此工具的 USB 定位功能适用于 iOS 17.4 或更新版本。")
        async with MobileImageMounterService(client) as mounter:
            try:
                developer_mode = await mounter.query_developer_mode_status()
            except MessageNotSupportedError:
                # Some builds cannot report this here. Opening the developer service detects it.
                developer_mode = None
        if developer_mode is False:
            raise _UserError(DEVELOPER_MODE_HELP)
        return _Phone(serial, version_parts.group(0), int(version_parts[1]))


async def _tunnel_provider(phone):
    from pymobiledevice3.remote.tunnel_service import CoreDeviceTunnelProxy
    from pymobiledevice3.remote.userspace_tunnel import TunnelProvider

    client = await _lockdown(phone.serial)
    try:
        proxy = await CoreDeviceTunnelProxy.create(client)
    except BaseException:
        await client.close()
        raise
    return TunnelProvider(proxy, client)


async def _mount(phone, rsd):
    from pymobiledevice3.exceptions import AlreadyMountedError, DeveloperModeIsNotEnabledError, RSDRequiredError
    from pymobiledevice3.services.mobile_image_mounter import auto_mount

    try:
        # The USB image mounter transfers older disk images faster than the userspace tunnel.
        if phone.major < 27:
            async with await _lockdown(phone.serial) as client:
                try:
                    await auto_mount(client)
                    return
                except RSDRequiredError:
                    pass
        await auto_mount(rsd)
    except AlreadyMountedError:
        pass
    except DeveloperModeIsNotEnabledError as error:
        raise _UserError(DEVELOPER_MODE_HELP) from error
    except Exception as error:
        raise _UserError(
            "未能准备 iPhone 定位组件。请确认电脑已联网、iPhone 已解锁并开启开发者模式，然后重试。"
        ) from error


async def _open_session(phone, prepare):
    from pymobiledevice3.dtx.exceptions import DTXNsError
    from pymobiledevice3.exceptions import InvalidServiceError
    from pymobiledevice3.remote.userspace_tunnel import UserspaceRsdTunnel
    from pymobiledevice3.services.dvt.instruments.dvt_provider import DvtProvider
    from pymobiledevice3.services.dvt.instruments.location_simulation import LocationSimulation

    async def provider():
        return await _tunnel_provider(phone)

    for attempt in range(2):
        async with AsyncExitStack() as stack:
            rsd = await stack.enter_async_context(UserspaceRsdTunnel(provider_factory=provider))
            try:
                dvt = await stack.enter_async_context(DvtProvider(rsd))
                simulation = await stack.enter_async_context(LocationSimulation(dvt))
            except (InvalidServiceError, ConnectionRefusedError, DTXNsError):
                if not prepare or attempt:
                    raise _UserError("无法打开 iPhone 的定位开发服务。请确认开发者模式已启用，解锁手机后重试。")
                await _mount(phone, rsd)
                # A fresh tunnel gets the device's updated service list.
                continue
            return stack.pop_all(), simulation
    raise _UserError("无法打开 iPhone 的定位开发服务。")


class _DeviceSession:
    def __init__(self):
        self.lock = asyncio.Lock()
        self.phone = None
        self.stack = None
        self.simulation = None
        self.point = None
        self.current = None

    async def close(self):
        stack = self.stack
        self.stack = None
        self.simulation = None
        self.point = None
        if stack is not None:
            with suppress(Exception):
                await asyncio.wait_for(stack.aclose(), 5)

    async def _select(self):
        phone = await asyncio.wait_for(_phone(), 12)
        if self.phone is not None and self.phone.serial != phone.serial:
            raise _UserError("请重新连接原来设置定位的 iPhone，并恢复真实定位后再更换手机。")
        return phone

    async def status(self):
        phone = await self._select()
        active = self.point is not None
        message = f"iPhone 已连接（iOS {phone.version}）。"
        if active:
            message += "模拟坐标已发送。"
        return {"ok": True, "message": message, "active": active}

    async def _send_clear(self, phone):
        if self.simulation is not None:
            try:
                await asyncio.wait_for(self.simulation.clear(), 5)
                return
            except Exception:
                await self.close()
        stack, simulation = await asyncio.wait_for(_open_session(phone, prepare=False), 18)
        async with stack:
            await asyncio.wait_for(simulation.clear(), 5)

    async def apply(self, point):
        phone = await self._select()
        previous = self.point
        if self.simulation is None:
            self.stack, self.simulation = await asyncio.wait_for(_open_session(phone, prepare=True), 240)
        if _closing.is_set():
            raise _UserError("工具正在退出，未发送新的模拟坐标。")
        self.phone = phone
        try:
            async with asyncio.timeout(10):
                await self.simulation.set(*point)
        except Exception:
            if previous is not None and not _closing.is_set():
                try:
                    async with asyncio.timeout(5):
                        await self.simulation.set(*previous)
                except Exception:
                    pass
                else:
                    raise _UserError("本次坐标发送失败，已重新发送上一次的模拟坐标。")
            try:
                await self._send_clear(phone)
                self.phone = None
                message = "本次坐标发送失败，已发送恢复真实定位指令。请在 iPhone 系统「地图」确认。"
            except Exception:
                message = "本次坐标发送失败，恢复状态无法确认。" + RESTORE_HELP
            finally:
                await self.close()
            raise _UserError(message)
        self.point = point
        return {
            "ok": True,
            "message": "已发送模拟坐标。请保持 USB 连接和本工具运行，并在 iPhone 系统「地图」点定位箭头核对。",
            "active": True,
        }

    async def clear(self):
        phone = await self._select()
        try:
            await self._send_clear(phone)
        except Exception as error:
            raise _UserError("未能确认恢复指令已发送。" + RESTORE_HELP) from error
        finally:
            await self.close()
        self.phone = None
        return {"ok": True, "message": "已发送恢复真实定位指令。请在 iPhone 系统「地图」确认。", "active": False}

    async def shutdown(self):
        if self.current is not None:
            self.current.cancel()
            await asyncio.wait({self.current}, timeout=2)
        if self.phone is not None:
            with suppress(Exception):
                if self.simulation is not None:
                    await asyncio.wait_for(self.simulation.clear(), 3)
                elif self.current is None:
                    await asyncio.wait_for(self._send_clear(self.phone), 3)
        await self.close()


_worker_lock = threading.Lock()
_closing = threading.Event()
_loop = None
_session = None


def _worker():
    global _loop, _session
    if _loop is None:
        _loop = asyncio.new_event_loop()
        _session = _DeviceSession()
        threading.Thread(target=_loop.run_forever, name="iphone-usb", daemon=True).start()
    return _loop, _session


async def _call(session, operation, args):
    _dependencies()
    from pymobiledevice3.exceptions import (
        ConnectionFailedToUsbmuxdError,
        DeveloperModeIsNotEnabledError,
        NotPairedError,
        PairingError,
        PasscodeRequiredError,
    )
    try:
        async with session.lock:
            if _closing.is_set():
                raise _UserError("工具正在退出。")
            session.current = asyncio.current_task()
            try:
                return await getattr(session, operation)(*args)
            finally:
                session.current = None
    except DeveloperModeIsNotEnabledError as error:
        raise _UserError(DEVELOPER_MODE_HELP) from error
    except ConnectionFailedToUsbmuxdError as error:
        detail = "请安装并打开 Apple 设备 App 或 iTunes，再重试。" if IS_WINDOWS else "请重新连接 iPhone 后重试。"
        raise _UserError("无法连接 Apple 的 USB 设备服务。" + detail) from error
    except (NotPairedError, PairingError, PasscodeRequiredError) as error:
        raise _UserError("请解锁 iPhone，确认信任这台电脑，然后重试。") from error


def _run(operation, timeout, *args):
    with _worker_lock:
        if _closing.is_set():
            return {"ok": False, "message": "工具正在退出。"}
        loop, session = _worker()
        future = asyncio.run_coroutine_threadsafe(_call(session, operation, args), loop)
    try:
        return future.result(timeout=timeout)
    except _UserError as error:
        message = str(error)
    except FutureTimeout:
        future.cancel()
        message = "连接或操作超时。请保持 iPhone 解锁和 USB 连接，然后重试。"
    except ImportError:
        message = "USB 定位组件无法加载。请通过本工具的启动文件重新安装组件。"
    except Exception:
        message = "无法与 iPhone 通信。请检查数据线、解锁手机，并确认已信任这台电脑后重试。"
    return {"ok": False, "message": message}


def device_status():
    return _run("status", 15)


def set_location(lat, lon):
    try:
        if isinstance(lat, bool) or isinstance(lon, bool):
            raise ValueError
        point = (float(lat), float(lon))
        if not all(math.isfinite(value) for value in point):
            raise ValueError
        if not (-90 <= point[0] <= 90 and -180 <= point[1] <= 180):
            raise ValueError
    except (TypeError, ValueError, OverflowError):
        return {"ok": False, "message": "经纬度无效，请重新在地图上选点。"}
    return _run("apply", 300, point)


def clear_location():
    return _run("clear", 48)


@atexit.register
def shutdown_device():
    """On a normal tool exit, ask a still-connected iPhone to restore GPS."""
    with _worker_lock:
        if _closing.is_set():
            return
        _closing.set()
        if _loop is None:
            return
        future = asyncio.run_coroutine_threadsafe(_session.shutdown(), _loop)
    try:
        future.result(timeout=9)
    except FutureTimeout:
        future.cancel()
    except Exception:
        pass
