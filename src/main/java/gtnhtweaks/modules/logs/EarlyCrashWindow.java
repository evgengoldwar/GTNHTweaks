package gtnhtweaks.modules.logs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.plaf.metal.MetalLookAndFeel;

import net.minecraft.launchwrapper.Launch;

import gtnhtweaks.GTNHTweaks;

/**
 * A plain Java window for crashes the game cannot show itself: before its window exists, for example a mixin that
 * fails while Minecraft classes are loaded. It shows the problem in plain words and the crash report, with buttons
 * to copy the report and open the log folder, and keeps the game process alive until it is closed.
 * <p>
 * Plain Java and Swing only. Not shown on macOS, where AWT windows can hang a game started on the first thread, nor
 * without a display.
 */
public final class EarlyCrashWindow {

    private static final Color RED = new Color(0xE5534B);
    private static final Color ORANGE = new Color(0xF2A33A);
    // the SHADOW theme of the mod
    private static final Color BACKGROUND = new Color(0x2F2E2D);
    private static final Color PANEL = new Color(0x4A4948);
    private static final Color FIELD = new Color(0x1F1F1F);
    private static final Color BORDER = new Color(0x1C1C1D);
    private static final Color BUTTON = new Color(0x434241);
    private static final Color BUTTON_HOVER = new Color(0x555452);
    private static final Color LIGHT_EDGE = new Color(0x595857);
    private static final Color TEXT = new Color(0xE0E0E0);
    private static final Color TEXT_DIM = new Color(0xA09F9E);

    private static volatile boolean shown;

    private EarlyCrashWindow() {}

    /** Catches the exception that kills the thread that starts the game: the coremods are loaded on it. */
    static void install() {
        Thread launch = Thread.currentThread();
        Thread.UncaughtExceptionHandler previous = launch.getUncaughtExceptionHandler();
        launch.setUncaughtExceptionHandler((thread, error) -> {
            try {
                show(error, null, null);
            } catch (Throwable ignored) {
                // the normal output of the error below must never be lost
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /**
     * Shows the window and waits until it is closed.
     *
     * @param fullReport the crash report, or null to write one
     * @param reportFile where the report was saved, or null
     */
    public static void show(Throwable error, String fullReport, File reportFile) {
        if (shown || error == null || GraphicsEnvironment.isHeadless()
            || System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac")) return;
        shown = true;

        if (fullReport == null) {
            fullReport = report(error);
            reportFile = save(fullReport);
        }
        String problem = problem(error, fullReport);
        String headline = CrashDiagnosis.of(error).headline;

        CountDownLatch closed = new CountDownLatch(1);
        String report = fullReport;
        File file = reportFile;
        SwingUtilities.invokeLater(() -> {
            try {
                open(headline, problem, report, file, closed);
            } catch (Throwable t) {
                closed.countDown();
            }
        });
        try {
            closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
    }

    /** Dark Metal look: the colors of the parts Swing draws itself, set before they are made. */
    private static void darkLook() {
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Throwable ignored) {}
        Object[] colors = { "Panel.background", BACKGROUND, "Label.foreground", TEXT, "TabbedPane.background",
            BACKGROUND, "TabbedPane.foreground", TEXT, "TabbedPane.selected", PANEL, "TabbedPane.unselectedBackground",
            BUTTON, "TabbedPane.contentAreaColor", PANEL, "TabbedPane.light", LIGHT_EDGE, "TabbedPane.highlight",
            LIGHT_EDGE, "TabbedPane.shadow", BORDER, "TabbedPane.darkShadow", BORDER, "TabbedPane.focus", PANEL,
            "TabbedPane.borderHightlightColor", BORDER, "TabbedPane.tabAreaBackground", BACKGROUND,
            "ScrollBar.background", FIELD, "ScrollBar.track", FIELD, "ScrollBar.thumb", BUTTON,
            "ScrollBar.thumbHighlight", LIGHT_EDGE, "ScrollBar.thumbShadow", BORDER, "ScrollBar.thumbDarkShadow",
            BORDER, "ScrollBar.shadow", BORDER, "ScrollBar.highlight", FIELD, "ScrollBar.darkShadow", BORDER,
            "ScrollPane.background", FIELD, "Viewport.background", FIELD, "Button.background", BUTTON,
            "Button.foreground", TEXT, "Button.select", BUTTON_HOVER, "Button.focus", BUTTON };
        for (int i = 0; i < colors.length; i += 2) UIManager.put(colors[i], colors[i + 1]);
        // no Metal gradient on buttons and scroll bars
        UIManager.put("Button.gradient", null);
        UIManager.put("ScrollBar.gradient", null);
    }

    private static JButton button(String label) {
        JButton button = new JButton(label);
        button.setBackground(BUTTON);
        button.setForeground(TEXT);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 1, 0, 0, LIGHT_EDGE),
                    BorderFactory.createEmptyBorder(5, 14, 5, 14))));
        button.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(BUTTON_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(BUTTON);
            }
        });
        return button;
    }

    private static void open(String headline, String problem, String report, File file, CountDownLatch closed) {
        darkLook();
        JFrame frame = new JFrame("GTNHTweaks - the game could not start");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosed(WindowEvent e) {
                closed.countDown();
            }
        });

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        header.setBackground(BACKGROUND);
        JLabel title = new JLabel("The game could not start");
        title.setFont(
            title.getFont()
                .deriveFont(Font.BOLD, 18F));
        title.setForeground(RED);
        JLabel problemLabel = new JLabel("Problem: " + headline);
        problemLabel.setFont(
            problemLabel.getFont()
                .deriveFont(Font.BOLD, 14F));
        problemLabel.setForeground(ORANGE);
        JLabel hint = new JLabel(
            "The game crashed before its window opened. Copy the report for a bug report, then close this window.");
        hint.setForeground(TEXT_DIM);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(problemLabel);
        header.add(Box.createVerticalStrut(4));
        header.add(hint);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BACKGROUND);
        tabs.setForeground(TEXT);
        tabs.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        tabs.addTab("Problem", textPane(problem));
        tabs.addTab("Crash report", textPane(report));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        buttons.setBackground(BACKGROUND);
        JButton copy = button("Copy report");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(report), null);
            copy.setText("Copied!");
        });
        buttons.add(copy);
        JButton folder = button("Open log folder");
        folder.addActionListener(e -> openFile(LogFiles.folder()));
        buttons.add(folder);
        if (file != null) {
            JButton openReport = button("Open report");
            openReport.addActionListener(e -> openFile(file));
            buttons.add(openReport);
        }
        JButton close = button("Close");
        close.addActionListener(e -> frame.dispose());
        buttons.add(close);

        frame.getContentPane()
            .setBackground(BACKGROUND);
        frame.getContentPane()
            .setLayout(new BorderLayout());
        frame.getContentPane()
            .add(header, BorderLayout.NORTH);
        frame.getContentPane()
            .add(tabs, BorderLayout.CENTER);
        frame.getContentPane()
            .add(buttons, BorderLayout.SOUTH);
        frame.setPreferredSize(new Dimension(960, 640));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
    }

    private static JScrollPane textPane(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        area.setBackground(FIELD);
        area.setForeground(TEXT);
        area.setCaretColor(TEXT);
        area.setSelectionColor(BUTTON_HOVER);
        area.setSelectedTextColor(Color.WHITE);
        area.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport()
            .setBackground(FIELD);
        return scroll;
    }

    private static void openFile(File file) {
        new Thread(() -> {
            try {
                Desktop.getDesktop()
                    .open(file);
            } catch (Throwable t) {
                GTNHTweaks.LOG.warn("Could not open " + file, t);
            }
        }, "GTNHTweaks open file").start();
    }

    // ---- text

    private static String problem(Throwable error, String report) {
        StringBuilder out = new StringBuilder();
        CrashDiagnosis.of(error)
            .appendTo(out);
        out.append('\n');
        try {
            out.append(CrashSummary.summarize(report, LogsModule.maxCrashStackFrames));
        } catch (Throwable t) {
            out.append(stackTrace(error));
        }
        try {
            List<String> errors = LogFiles.recentErrors();
            if (!errors.isEmpty()) {
                out.append("\nLAST ERRORS IN THE LOG\n");
                for (String line : errors) out.append(line)
                    .append('\n');
            }
        } catch (Throwable ignored) {}
        return out.toString();
    }

    /** A crash report in the layout of Minecraft, so the logs module and other tools read it as one. */
    private static String report(Throwable error) {
        return "---- Minecraft Crash Report ----\n" + "// The game crashed before its window opened (GTNHTweaks)\n\n"
            + "Time: "
            + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
            + "\n"
            + "Description: Starting the game\n\n"
            + stackTrace(error)
            + "\n\n-- System Details --\nDetails:\n"
            + "\tJava Version: "
            + System.getProperty("java.version")
            + ", "
            + System.getProperty("java.vendor")
            + "\n\tOperating System: "
            + System.getProperty("os.name")
            + " ("
            + System.getProperty("os.arch")
            + ") version "
            + System.getProperty("os.version")
            + "\n\tMemory: "
            + Runtime.getRuntime()
                .maxMemory() / 1024
                / 1024
            + " MB max\n";
    }

    private static File save(String report) {
        try {
            File home = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
            File dir = new File(home, "crash-reports");
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            File file = new File(
                dir,
                "crash-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + "-client.txt");
            try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                out.write(report);
            }
            return file;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String stackTrace(Throwable t) {
        StringWriter out = new StringWriter();
        t.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
