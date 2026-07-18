package io.dbeaver.mcp.ui;

import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.internal.WorkbenchWindow;

import io.dbeaver.mcp.Activator;
import io.dbeaver.mcp.McpServerPreferences;

/**
 * Shows the MCP server's running/stopped state in the workbench status line.
 * DBeaver's product does not render generic
 * {@code toolbar:org.eclipse.ui.trim.status} control contributions, so this
 * attaches directly to each window's
 * {@link org.eclipse.ui.internal.WorkbenchWindow}'s {@link IStatusLineManager}
 * instead, which DBeaver's RCP shell does honor. Installed from
 * {@link io.dbeaver.mcp.McpPluginService} via {@link #install()}.
 */
public final class McpStatusBarContribution {

    private static final String ITEM_ID = "io.dbeaver.mcp.statusBarItem";

    private static McpStatusBarContribution instance;

    private final IWorkbench workbench;
    private final Display display;
    private final Activator.ServerStateListener stateListener = this::onServerStateChanged;
    private final IWindowListener windowListener = new IWindowListener() {
        @Override
        public void windowOpened(IWorkbenchWindow window) {
            attach(window);
        }

        @Override
        public void windowActivated(IWorkbenchWindow window) {
        }

        @Override
        public void windowDeactivated(IWorkbenchWindow window) {
        }

        @Override
        public void windowClosed(IWorkbenchWindow window) {
        }
    };

    private IStatusLineManager statusLine;
    private Label statusLabel;

    private McpStatusBarContribution() {
        workbench = PlatformUI.getWorkbench();
        display = workbench.getDisplay();
    }

    /** Idempotent; safe to call multiple times (e.g. re-entrant startup). */
    public static synchronized void install() {
        if (instance != null) {
            return;
        }
        instance = new McpStatusBarContribution();
        instance.doInstall();
    }

    /** Idempotent; safe from the DBeaver or OSGi shutdown thread. */
    public static synchronized void uninstall() {
        McpStatusBarContribution current = instance;
        instance = null;
        if (current != null) {
            current.doUninstall();
        }
    }

    private void doInstall() {
        display.asyncExec(() -> {
            if (display.isDisposed()) {
                return;
            }
            for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
                attach(window);
            }
            workbench.addWindowListener(windowListener);
        });
    }

    private void doUninstall() {
        Activator activator = Activator.getDefault();
        if (activator != null) {
            activator.removeServerStateListener(stateListener);
        }
        if (display.isDisposed()) {
            return;
        }
        display.asyncExec(() -> {
            if (display.isDisposed()) {
                return;
            }
            workbench.removeWindowListener(windowListener);
            if (statusLine != null) {
                statusLine.remove(ITEM_ID);
                statusLine.update(true);
                statusLine = null;
            }
            statusLabel = null;
        });
    }

    private void attach(IWorkbenchWindow window) {
        if (statusLabel != null && !statusLabel.isDisposed()) {
            // Only one status line item is needed; DBeaver is single-window in practice.
            return;
        }
        if (!(window instanceof WorkbenchWindow)) {
            return;
        }
        statusLine = ((WorkbenchWindow) window).getStatusLineManager();
        if (statusLine == null) {
            return;
        }
        statusLine.add(new ContributionItem(ITEM_ID) {
            @Override
            public void fill(Composite parent) {
                Composite wrapper = new Composite(parent, SWT.NONE);
                GridLayout wrapperLayout = new GridLayout(1, false);
                wrapperLayout.marginWidth = 0;
                wrapperLayout.marginHeight = 0;
                wrapper.setLayout(wrapperLayout);

                statusLabel = new Label(wrapper, SWT.NONE);
                statusLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true));
                statusLabel.setToolTipText("Click to manage the DBeaver MCP HTTP server");
                statusLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseUp(MouseEvent e) {
                        showMenu(statusLabel);
                    }
                });

                Activator activator = Activator.getDefault();
                if (activator != null) {
                    activator.addServerStateListener(stateListener);
                    refresh(activator.isServerRunning());
                } else {
                    refresh(false);
                }

                statusLabel.addDisposeListener(e -> {
                    Activator a = Activator.getDefault();
                    if (a != null) {
                        a.removeServerStateListener(stateListener);
                    }
                });
            }
        });
        statusLine.update(true);
    }

    private void onServerStateChanged(boolean running) {
        if (display.isDisposed()) {
            return;
        }
        display.asyncExec(() -> {
            if (statusLabel != null && !statusLabel.isDisposed()) {
                refresh(running);
            }
        });
    }

    private void refresh(boolean running) {
        String host = McpServerPreferences.getHost();
        int port = McpServerPreferences.getPort();
        Display labelDisplay = statusLabel.getDisplay();
        Color color = running ? labelDisplay.getSystemColor(SWT.COLOR_DARK_GREEN)
                : labelDisplay.getSystemColor(SWT.COLOR_RED);
        statusLabel.setForeground(color);
        statusLabel.setText(running ? "● MCP: running (" + host + ":" + port + ")" : "● MCP: stopped");
        statusLabel.pack();
        Composite wrapper = statusLabel.getParent();
        if (wrapper != null) {
            wrapper.pack();
            wrapper.layout();
            Composite statusLineComposite = wrapper.getParent();
            if (statusLineComposite != null) {
                statusLineComposite.layout(true, true);
            }
        }
    }

    private void showMenu(Control control) {
        Menu menu = new Menu(control);
        boolean running = Activator.getDefault() != null && Activator.getDefault().isServerRunning();

        MenuItem startItem = new MenuItem(menu, SWT.PUSH);
        startItem.setText("Start Server");
        startItem.setEnabled(!running);
        startItem.addListener(SWT.Selection, e -> runCommand("io.dbeaver.mcp.commands.startServer"));

        MenuItem stopItem = new MenuItem(menu, SWT.PUSH);
        stopItem.setText("Stop Server");
        stopItem.setEnabled(running);
        stopItem.addListener(SWT.Selection, e -> runCommand("io.dbeaver.mcp.commands.stopServer"));

        new MenuItem(menu, SWT.SEPARATOR);

        MenuItem connectionsItem = new MenuItem(menu, SWT.PUSH);
        connectionsItem.setText("Connections...");
        connectionsItem.addListener(SWT.Selection, e -> runCommand("io.dbeaver.mcp.commands.showConnections"));

        MenuItem settingsItem = new MenuItem(menu, SWT.PUSH);
        settingsItem.setText("Settings...");
        settingsItem.addListener(SWT.Selection, e -> runCommand("io.dbeaver.mcp.commands.openPreferences"));

        menu.setVisible(true);
    }

    private void runCommand(String commandId) {
        try {
            IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
            if (window == null) {
                return;
            }
            IHandlerService handlerService = window.getService(IHandlerService.class);
            if (handlerService != null) {
                handlerService.executeCommand(commandId, null);
            }
        } catch (Exception e) {
            System.out.println("[dbeaver-mcp] Failed to run command " + commandId + ": " + e.getMessage());
        }
    }
}
