$code = @'
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public class VernInteractiveRunner {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct STARTUPINFO {
        public Int32 cb;
        public string lpReserved;
        public string lpDesktop;
        public string lpTitle;
        public Int32 dwX;
        public Int32 dwY;
        public Int32 dwXSize;
        public Int32 dwYSize;
        public Int32 dwXCountChars;
        public Int32 dwYCountChars;
        public Int32 dwFillAttribute;
        public Int32 dwFlags;
        public Int16 wShowWindow;
        public Int16 cbReserved2;
        public IntPtr lpReserved2;
        public IntPtr hStdInput;
        public IntPtr hStdOutput;
        public IntPtr hStdError;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct PROCESS_INFORMATION {
        public IntPtr hProcess;
        public IntPtr hThread;
        public Int32 dwProcessId;
        public Int32 dwThreadId;
    }

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern bool CreateProcess(
        string lpApplicationName,
        string lpCommandLine,
        IntPtr lpProcessAttributes,
        IntPtr lpThreadAttributes,
        bool bInheritHandles,
        uint dwCreationFlags,
        IntPtr lpEnvironment,
        string lpCurrentDirectory,
        ref STARTUPINFO lpStartupInfo,
        out PROCESS_INFORMATION lpProcessInformation);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern UInt32 WaitForSingleObject(IntPtr hHandle, UInt32 dwMilliseconds);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern IntPtr OpenDesktop(string lpszDesktop, uint dwFlags, bool fInherit, uint dwDesiredAccess);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool SetThreadDesktop(IntPtr hDesktop);

    [DllImport("user32.dll")]
    public static extern bool CloseDesktop(IntPtr hDesktop);

    public delegate bool CallBack(IntPtr hwnd, int lParam);

    [DllImport("user32.dll")]
    public static extern int EnumWindows(CallBack x, int y);

    [DllImport("user32.dll")]
    public static extern int GetWindowText(IntPtr hwnd, StringBuilder lPtrString, int maxCount);

    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hwnd);

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint processId);

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    const uint GENERIC_ALL = 0x10000000;
    const UInt32 INFINITE = 0xFFFFFFFF;

    public static void Run(string appPath, string workDir) {
        STARTUPINFO si = new STARTUPINFO();
        si.cb = Marshal.SizeOf(si);
        si.lpDesktop = @"winsta0\default";

        PROCESS_INFORMATION pi = new PROCESS_INFORMATION();
        string cmd = "\"" + appPath + "\"";
        bool success = CreateProcess(appPath, cmd, IntPtr.Zero, IntPtr.Zero, false, 0, IntPtr.Zero, workDir, ref si, out pi);
        if (!success) {
            Console.WriteLine("CreateProcess failed: " + Marshal.GetLastWin32Error());
            return;
        }
        Console.WriteLine("Vern launched with PID: " + pi.dwProcessId);

        // Wait for window to appear and bring it to foreground
        for (int i = 0; i < 15; i++) {
            Thread.Sleep(1000);
            IntPtr foundHwnd = IntPtr.Zero;
            Thread t = new Thread(() => {
                IntPtr hDesk = OpenDesktop("Default", 0, false, GENERIC_ALL);
                if (hDesk != IntPtr.Zero) {
                    SetThreadDesktop(hDesk);
                    EnumWindows((hwnd, lParam) => {
                        uint pid;
                        GetWindowThreadProcessId(hwnd, out pid);
                        if (pid == pi.dwProcessId && IsWindowVisible(hwnd)) {
                            StringBuilder sb = new StringBuilder(256);
                            GetWindowText(hwnd, sb, 256);
                            if (sb.ToString().Contains("Vern")) {
                                foundHwnd = hwnd;
                                return false; // Stop enumerating
                            }
                        }
                        return true;
                    }, 0);
                    CloseDesktop(hDesk);
                }
            });
            t.Start();
            t.Join();

            if (foundHwnd != IntPtr.Zero) {
                Console.WriteLine("Bringing Vern window (" + foundHwnd + ") to foreground!");
                ShowWindow(foundHwnd, 9); // SW_RESTORE
                SetForegroundWindow(foundHwnd);
                break;
            }
        }

        Console.WriteLine("Vern is active on user screen. Waiting on process...");
        WaitForSingleObject(pi.hProcess, INFINITE);
        Console.WriteLine("Vern closed.");
    }
}
'@

Add-Type -TypeDefinition $code
$app = "C:\Users\wwwfe\Downloads\Veritas Reader Android\desktop\src-tauri\target\debug\vern-desktop.exe"
$dir = "C:\Users\wwwfe\Downloads\Veritas Reader Android\desktop"
[VernInteractiveRunner]::Run($app, $dir)
