"""Send CTRL_C_EVENT only to a newly-created console containing exactly this helper and the test JVM."""
import ctypes,os,sys
from ctypes import wintypes
pid=int(sys.argv[1])
k=ctypes.WinDLL("kernel32",use_last_error=True)
k.FreeConsole()
if not k.AttachConsole(pid):raise ctypes.WinError(ctypes.get_last_error())
try:
    ids=(wintypes.DWORD*8)()
    n=k.GetConsoleProcessList(ids,len(ids))
    if n!=2 or set(ids[:n])!={pid,os.getpid()}:raise RuntimeError("Not an isolated test console")
    if not k.SetConsoleCtrlHandler(None,True):raise ctypes.WinError(ctypes.get_last_error())
    if not k.GenerateConsoleCtrlEvent(0,0):raise ctypes.WinError(ctypes.get_last_error())
finally:
    k.FreeConsole()
