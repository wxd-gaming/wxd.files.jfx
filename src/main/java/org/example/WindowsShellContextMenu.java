package org.example;

import com.sun.jna.*;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.ByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.io.File;
import java.nio.file.Path;

final class WindowsShellContextMenu {

    private static final Guid.IID IID_ISHELL_FOLDER = new Guid.IID("{000214E6-0000-0000-C000-000000000046}");
    private static final Guid.IID IID_ICONTEXT_MENU = new Guid.IID("{000214E4-0000-0000-C000-000000000046}");
    private static final Guid.IID IID_ICONTEXT_MENU2 = new Guid.IID("{000214F4-0000-0000-C000-000000000046}");
    private static final Guid.IID IID_ICONTEXT_MENU3 = new Guid.IID("{BCFCE0A0-EC17-11D0-8D10-00A0C90F2719}");

    private static final int CMF_NORMAL = 0x00000000;
    private static final int TPM_RETURNCMD = 0x0100;
    private static final int TPM_RIGHTBUTTON = 0x0002;
    private static final int WM_INITMENUPOPUP = 0x0117;
    private static final int WM_DRAWITEM = 0x002B;
    private static final int WM_MEASUREITEM = 0x002C;
    private static final int WM_MENUCHAR = 0x0120;

    private WindowsShellContextMenu() {
    }

    static boolean show(Path path, double screenX, double screenY) {
        if (!isWindows()) {
            return false;
        }

        WinNT.HRESULT coInitResult = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED);
        int coInitCode = coInitResult.intValue();
        boolean shouldUninitialize = coInitCode == WinError.S_OK.intValue() || coInitCode == WinError.S_FALSE.intValue();
        if (coInitCode != WinError.S_OK.intValue()
                && coInitCode != WinError.S_FALSE.intValue()
                && coInitCode != WinError.RPC_E_CHANGED_MODE) {
            return false;
        }

        Pointer absolutePidl = null;
        Pointer childPidl = null;
        IShellFolder parentFolder = null;
        IContextMenu contextMenu = null;
        IContextMenu2 contextMenu2 = null;
        IContextMenu3 contextMenu3 = null;
        WinDef.HMENU menu = null;

        try {
            PointerByReference pidlRef = new PointerByReference();
            WinNT.HRESULT parseResult = Shell32Ext.INSTANCE.SHParseDisplayName(
                    new WString(path.toAbsolutePath().toString()),
                    Pointer.NULL,
                    pidlRef,
                    0,
                    null
            );
            if (COMUtils.FAILED(parseResult)) {
                return false;
            }
            absolutePidl = pidlRef.getValue();

            PointerByReference parentFolderRef = new PointerByReference();
            PointerByReference childPidlRef = new PointerByReference();
            WinNT.HRESULT bindResult = Shell32Ext.INSTANCE.SHBindToParent(
                    absolutePidl,
                    IID_ISHELL_FOLDER,
                    parentFolderRef,
                    childPidlRef
            );
            if (COMUtils.FAILED(bindResult)) {
                return false;
            }

            parentFolder = new IShellFolder(parentFolderRef.getValue());
            childPidl = childPidlRef.getValue();

            PointerByReference menuRef = new PointerByReference();
            WinNT.HRESULT uiObjectResult = parentFolder.getUIObjectOf(
                    MenuHostWindow.HWND_OWNER,
                    1,
                    new Pointer[]{childPidl},
                    IID_ICONTEXT_MENU,
                    menuRef
            );
            if (COMUtils.FAILED(uiObjectResult)) {
                return false;
            }
            contextMenu = new IContextMenu(menuRef.getValue());

            PointerByReference contextMenu3Ref = new PointerByReference();
            if (COMUtils.SUCCEEDED(contextMenu.queryInterface(IID_ICONTEXT_MENU3, contextMenu3Ref))) {
                contextMenu3 = new IContextMenu3(contextMenu3Ref.getValue());
                contextMenu2 = contextMenu3;
            } else {
                PointerByReference contextMenu2Ref = new PointerByReference();
                if (COMUtils.SUCCEEDED(contextMenu.queryInterface(IID_ICONTEXT_MENU2, contextMenu2Ref))) {
                    contextMenu2 = new IContextMenu2(contextMenu2Ref.getValue());
                }
            }

            menu = User32Ext.INSTANCE.CreatePopupMenu();
            if (menu == null) {
                return false;
            }

            WinNT.HRESULT queryResult = contextMenu.queryContextMenu(menu, 0, 1, 0x7FFF, CMF_NORMAL);
            if (COMUtils.FAILED(queryResult)) {
                return false;
            }

            MenuHostWindow.installHandlers(contextMenu2, contextMenu3);
            int commandId = User32Ext.INSTANCE.TrackPopupMenu(
                    menu,
                    TPM_RETURNCMD | TPM_RIGHTBUTTON,
                    (int) Math.round(screenX),
                    (int) Math.round(screenY),
                    0,
                    MenuHostWindow.HWND_OWNER,
                    null
            );
            if (commandId > 0) {
                CMINVOKECOMMANDINFO invoke = new CMINVOKECOMMANDINFO();
                invoke.cbSize = invoke.size();
                invoke.hwnd = MenuHostWindow.HWND_OWNER;
                invoke.lpVerb = Pointer.createConstant(commandId - 1L);
                invoke.nShow = 1;
                invoke.write();
                WinNT.HRESULT invokeResult = contextMenu.invokeCommand(invoke);
                return COMUtils.SUCCEEDED(invokeResult);
            }
            return true;
        } finally {
            MenuHostWindow.clearHandlers();
            if (menu != null) {
                User32Ext.INSTANCE.DestroyMenu(menu);
            }
            if (contextMenu3 != null) {
                contextMenu3.release();
            }
            if (contextMenu2 != null && contextMenu2 != contextMenu3) {
                contextMenu2.release();
            }
            if (contextMenu != null) {
                contextMenu.release();
            }
            if (parentFolder != null) {
                parentFolder.release();
            }
            if (absolutePidl != null) {
                Ole32.INSTANCE.CoTaskMemFree(absolutePidl);
            }
            if (shouldUninitialize) {
                Ole32.INSTANCE.CoUninitialize();
            }
        }
    }

    private static boolean isWindows() {
        return File.separatorChar == '\\';
    }

    interface Shell32Ext extends StdCallLibrary {
        Shell32Ext INSTANCE = Native.load("shell32", Shell32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        WinNT.HRESULT SHParseDisplayName(WString name, Pointer bindContext, PointerByReference pidl, int sfgaoIn, IntByReference psfgaoOut);

        WinNT.HRESULT SHBindToParent(Pointer pidl, Guid.IID riid, PointerByReference ppv, PointerByReference ppidlLast);
    }

    interface User32Ext extends StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        WinDef.HMENU CreatePopupMenu();

        boolean DestroyMenu(WinDef.HMENU menu);

        int TrackPopupMenu(WinDef.HMENU menu, int flags, int x, int y, int reserved, WinDef.HWND owner, WinDef.RECT rect);

        short RegisterClassEx(WinUser.WNDCLASSEX windowClass);

        WinDef.HWND CreateWindowEx(
                int exStyle,
                String className,
                String windowName,
                int style,
                int x,
                int y,
                int width,
                int height,
                WinDef.HWND parent,
                WinDef.HMENU menu,
                WinDef.HINSTANCE instance,
                Pointer param
        );

        WinDef.LRESULT DefWindowProc(WinDef.HWND hwnd, int msg, WinDef.WPARAM wParam, WinDef.LPARAM lParam);
    }

    private static class UnknownCom {
        protected final Pointer pointer;
        private final Pointer vTable;

        UnknownCom(Pointer pointer) {
            this.pointer = pointer;
            this.vTable = pointer.getPointer(0);
        }

        WinNT.HRESULT queryInterface(Guid.IID iid, PointerByReference out) {
            iid.write();
            return new WinNT.HRESULT(invokeInt(0, pointer, iid, out));
        }

        int release() {
            return invokeInt(2, pointer);
        }

        int invokeInt(int vTableIndex, Object... args) {
            return getFunction(vTableIndex).invokeInt(args);
        }

        private Function getFunction(int vTableIndex) {
            Pointer address = vTable.getPointer((long) vTableIndex * Native.POINTER_SIZE);
            return Function.getFunction(address, Function.ALT_CONVENTION);
        }
    }

    private static final class IShellFolder extends UnknownCom {
        private IShellFolder(Pointer pointer) {
            super(pointer);
        }

        WinNT.HRESULT getUIObjectOf(
                WinDef.HWND owner,
                int count,
                Pointer[] pidls,
                Guid.IID iid,
                PointerByReference out
        ) {
            iid.write();
            return new WinNT.HRESULT(invokeInt(
                    10,
                    pointer,
                    owner,
                    count,
                    pidls,
                    iid,
                    Pointer.NULL,
                    out
            ));
        }
    }

    private static class IContextMenu extends UnknownCom {
        private IContextMenu(Pointer pointer) {
            super(pointer);
        }

        WinNT.HRESULT queryContextMenu(WinDef.HMENU menu, int indexMenu, int idCmdFirst, int idCmdLast, int flags) {
            return new WinNT.HRESULT(invokeInt(3, pointer, menu, indexMenu, idCmdFirst, idCmdLast, flags));
        }

        WinNT.HRESULT invokeCommand(CMINVOKECOMMANDINFO commandInfo) {
            return new WinNT.HRESULT(invokeInt(4, pointer, commandInfo.getPointer()));
        }
    }

    private static class IContextMenu2 extends IContextMenu {
        private IContextMenu2(Pointer pointer) {
            super(pointer);
        }

        WinNT.HRESULT handleMenuMsg(int message, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
            return new WinNT.HRESULT(invokeInt(6, pointer, message, wParam, lParam));
        }
    }

    private static final class IContextMenu3 extends IContextMenu2 {
        private IContextMenu3(Pointer pointer) {
            super(pointer);
        }

        WinNT.HRESULT handleMenuMsg2(int message, WinDef.WPARAM wParam, WinDef.LPARAM lParam, LResultByReference result) {
            return new WinNT.HRESULT(invokeInt(7, pointer, message, wParam, lParam, result));
        }
    }

    private static final class LResultByReference extends ByReference {
        private LResultByReference() {
            super(Native.POINTER_SIZE);
        }

        WinDef.LRESULT getValue() {
            long value = Native.POINTER_SIZE == 8 ? getPointer().getLong(0) : getPointer().getInt(0);
            return new WinDef.LRESULT(value);
        }
    }

    @Structure.FieldOrder({"cbSize", "fMask", "hwnd", "lpVerb", "lpParameters", "lpDirectory", "nShow", "dwHotKey", "hIcon"})
    public static class CMINVOKECOMMANDINFO extends Structure {
        public int cbSize;
        public int fMask;
        public WinDef.HWND hwnd;
        public Pointer lpVerb;
        public String lpParameters;
        public String lpDirectory;
        public int nShow;
        public int dwHotKey;
        public Pointer hIcon;
    }

    private static final class MenuHostWindow {
        private static final String CLASS_NAME = "JfxShellMenuHost";
        private static final WinDef.HWND HWND_OWNER;
        private static final WindowProcCallback WINDOW_PROC = new WindowProcCallback();
        private static volatile IContextMenu2 currentMenu2;
        private static volatile IContextMenu3 currentMenu3;

        static {
            WinDef.HINSTANCE hInstance = Kernel32.INSTANCE.GetModuleHandle(null);
            WinUser.WNDCLASSEX windowClass = new WinUser.WNDCLASSEX();
            windowClass.cbSize = windowClass.size();
            windowClass.lpfnWndProc = WINDOW_PROC;
            windowClass.hInstance = hInstance;
            windowClass.lpszClassName = CLASS_NAME;
            User32Ext.INSTANCE.RegisterClassEx(windowClass);

            HWND_OWNER = User32Ext.INSTANCE.CreateWindowEx(
                    0,
                    CLASS_NAME,
                    CLASS_NAME,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null,
                    hInstance,
                    null
            );
        }

        private MenuHostWindow() {
        }

        static void installHandlers(IContextMenu2 menu2, IContextMenu3 menu3) {
            currentMenu2 = menu2;
            currentMenu3 = menu3;
        }

        static void clearHandlers() {
            currentMenu3 = null;
            currentMenu2 = null;
        }

        private static final class WindowProcCallback implements WinUser.WindowProc, Callback {
            @Override
            public WinDef.LRESULT callback(WinDef.HWND hwnd, int uMsg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
                IContextMenu3 menu3 = currentMenu3;
                if (menu3 != null && uMsg == WM_MENUCHAR) {
                    LResultByReference resultRef = new LResultByReference();
                    WinNT.HRESULT hr = menu3.handleMenuMsg2(uMsg, wParam, lParam, resultRef);
                    if (COMUtils.SUCCEEDED(hr)) {
                        return resultRef.getValue();
                    }
                }

                IContextMenu2 menu2 = currentMenu2;
                if (menu2 != null && isMenuMessage(uMsg)) {
                    WinNT.HRESULT hr = menu2.handleMenuMsg(uMsg, wParam, lParam);
                    if (COMUtils.SUCCEEDED(hr)) {
                        return new WinDef.LRESULT(0);
                    }
                }

                return User32Ext.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
            }

            private boolean isMenuMessage(int message) {
                return message == WM_INITMENUPOPUP || message == WM_DRAWITEM || message == WM_MEASUREITEM;
            }
        }
    }
}
