package mydiary;

import javax.swing.*; //Core Swing UI components (JFrame, JButton etc)
import javax.swing.border.*; //Border styles for components (EmptyBorder, LineBorder, TitledBorder etc)
import javax.swing.event.*; //Swing event listeners (ListSelectionListener, DocumentListener etc)
import java.awt.*; //Core AWT classes (Color, Font, Dimension etc)
import java.awt.event.*; //AWT event handlers (ActionListener, MouseListener etc)
import java.io.*; //File input/output (File, FileReader/writer IOException etc)
import java.util.List; //used to store diary entries list
import java.util.Properties; //Load key-value config files (ex: for DB credentials)

public class DiaryApp extends JFrame {

    // ── Glassmorphism Palette ─────────────────────────────────────────────
    private static final Color BG_TOP      = new Color(0xA8D8EA);   // gradient top
    private static final Color BG_BOT      = new Color(0xCDB4DB);   // gradient bottom
    private static final Color BG         = new Color(0xB8C8E8);
    private static final Color GLASS      = new Color(255, 255, 255, 60);   // frosted glass fill
    private static final Color GLASS_BORDER= new Color(255, 255, 255, 120); // glass edge
    private static final Color SIDEBAR_BG = new Color(255, 255, 255, 45);
    private static final Color ACCENT     = new Color(0x7B61FF);   // vivid purple
    private static final Color ACCENT2    = new Color(0x4FC3F7);   // sky blue
    private static final Color ACCENT3    = new Color(0xF48FB1);   // soft pink
    private static final Color TEXT_DARK  = new Color(0x1A1040);
    private static final Color TEXT_LIGHT = new Color(255, 255, 255, 180);
    private static final Color TEXT_WHITE = new Color(0xFFFFFF);
    private static final Color CARD_BG    = new Color(255, 255, 255, 70);
    private static final Color INPUT_BG   = new Color(255, 255, 255, 80);
    private static final Color AI_BG      = new Color(200, 220, 255, 50);
    private static final Color DIVIDER    = new Color(255, 255, 255, 80);
    private static final Color REC_RED    = new Color(0xFF6B6B);

    // ── Fonts ─────────────────────────────────────────────────────────────
    private static final Font FONT_TITLE   = new Font("Segoe UI", Font.BOLD,  22);
    private static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD,  14);
    private static final Font FONT_BODY    = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_SMALL   = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_ENTRY   = new Font("Segoe UI", Font.PLAIN, 14);

    // ── Core objects ──────────────────────────────────────────────────────
    private final DiaryManager  manager  = new DiaryManager();
    private final MusicPlayer   music    = new MusicPlayer();
    private final VoiceRecorder recorder = new VoiceRecorder();
    private AIAssistant ai;

    // ── State ─────────────────────────────────────────────────────────────
    private DiaryEntry currentEntry = null;
    private boolean    editMode     = false;

    // ── UI components ─────────────────────────────────────────────────────
    private final DefaultListModel<DiaryEntry> listModel = new DefaultListModel<>();
    private JList<DiaryEntry> entryList;
    private JTextField        searchField;
    private JTextField        titleField;
    private JTextArea         contentArea;
    private JComboBox<String> moodBox;
    private JLabel            voiceLabel;
    private JTextArea         aiChatArea;
    private JTextField        aiInputField;
    private JLabel            statusLabel;
    private JLabel            musicLabel;
    private JButton           recordBtn;
    private JButton           saveBtn;
    private JButton           deleteBtn;
    private JButton           playVoiceBtn;
    private JTabbedPane       rightTabs;

    // ─────────────────────────────────────────────────────────────────────
    public DiaryApp() {
        initAI();
        buildUI();
        refreshList(manager.getAllEntries());
        setVisible(true);
    }

    // ── API key loading (robust) ──────────────────────────────────────────
    private void initAI() {
        String key = readApiKey();
        if (key.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                "Gemini API key not found.\n\n" +
                "Please make sure config.properties is in the project root folder\n" +
                "and contains the line:\n\n    gemini.api.key=YOUR_KEY_HERE",
                "API Key Missing", JOptionPane.WARNING_MESSAGE);
        }
        ai = new AIAssistant(key);
    }

    private String readApiKey() {
        // Look for config.properties in the working directory
        File cfg = new File("config.properties");

        // Fallback: look next to the running .class / .jar
        if (!cfg.exists()) {
            String jarDir = DiaryApp.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            cfg = new File(new File(jarDir).getParentFile(), "config.properties");
        }

        if (!cfg.exists()) {
            System.err.println("[DiaryApp] config.properties not found. Tried: "
                    + new File("config.properties").getAbsolutePath());
            return "";
        }

        try (InputStream in = new FileInputStream(cfg)) {
            Properties props = new Properties();
            props.load(in);
            String key = props.getProperty("groq.api.key", "").trim();
            if (key.isEmpty()) {
                System.err.println("[DiaryApp] gemini.api.key is empty in config.properties");
            } else {
                System.out.println("[DiaryApp] API key loaded OK (length " + key.length() + ")");
            }
            return key;
        } catch (IOException e) {
            System.err.println("[DiaryApp] Failed to read config.properties: " + e.getMessage());
            return "";
        }
    }

    // ── Build UI ──────────────────────────────────────────────────────────
    // KEY DESIGN RULE: Every panel must be opaque=true with a SOLID background
    // color. Mixing opaque=false with alpha paintComponent overrides breaks
    // Swing's RepaintManager — it skips clipping between siblings, causing the
    // JList cells to paint over the center panel. The gradient is achieved by
    // giving each region a solid color sampled from the gradient, not by alpha.
    private void buildUI() {
        setTitle("Eunoia");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        // Use the usable screen area (excludes taskbar, docks, IDE panels on same display)
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment()
                               .getMaximumWindowBounds();
        int w = Math.min(1060, usable.width);
        int h = Math.min(700,  usable.height);
        setSize(w, h);
        setMinimumSize(new Dimension(800, 520));
        // Center inside the usable area
        setLocation(usable.x + (usable.width  - w) / 2,
                    usable.y + (usable.height - h) / 2);
        setUndecorated(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setOpaque(true);
        root.setBackground(new Color(0xBDC9E5));
        root.setBorder(BorderFactory.createEmptyBorder());
        setContentPane(root);
        getRootPane().setBorder(BorderFactory.createEmptyBorder());

        root.add(buildTopBar(),    BorderLayout.NORTH);
        root.add(buildSidebar(),   BorderLayout.WEST);
        root.add(buildCenter(),    BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

        // Force layout pass so all components get correct bounds before painting
        root.revalidate();
        root.repaint();
    }

    // ── Top bar ───────────────────────────────────────────────────────────
    private JPanel buildTopBar() {
        // Solid color: BG_TOP blended with white overlay (~50/255 alpha on #A8D8EA)
        final Color TOP_BG = new Color(0xBFE3EF);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(true);
        bar.setBackground(TOP_BG);
        bar.setPreferredSize(new Dimension(0, 62));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xD0E8F0)));

        // Left — logo
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 16));
        left.setOpaque(false);
        left.setBorder(new EmptyBorder(0, 18, 0, 0));
        JLabel logo = new JLabel("Eunoia: Your Personal Journal");
        logo.setFont(new Font("Georgia", Font.BOLD, 24));
        logo.setForeground(new Color(0x4A2D8F));
        left.add(logo);

        // Right — music controls + new entry
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 14));
        right.setOpaque(false);

        musicLabel = new JLabel("Music: Off");
        musicLabel.setFont(FONT_SMALL);
        musicLabel.setForeground(new Color(0x4A2D8F));

        JButton musicBtn = pill("Lofi Music", new Color(0xD8EEF5));
        musicBtn.setForeground(new Color(0x4A2D8F));
        musicBtn.addActionListener(e -> toggleMusic());

        JSlider vol = new JSlider(0, 100, 50);
        vol.setPreferredSize(new Dimension(80, 20));
        vol.setOpaque(false);
        vol.setToolTipText("Volume");
        vol.addChangeListener(e -> music.setVolume(vol.getValue() / 100f));

        JButton newBtn = pill("+ New Entry", ACCENT);
        newBtn.setForeground(Color.WHITE);
        newBtn.addActionListener(e -> newEntry());

        right.add(musicLabel);
        right.add(musicBtn);
        right.add(vol);
        right.add(newBtn);

        bar.add(left,  BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ── Sidebar (entry list + search) ─────────────────────────────────────
    private JPanel buildSidebar() {
        final Color SIDE_BG = new Color(0xC2CEE6); // solid, opaque sidebar color

        JPanel side = new JPanel(new BorderLayout());
        side.setOpaque(true);
        side.setBackground(SIDE_BG);
        side.setPreferredSize(new Dimension(240, 0));
        side.setBorder(BorderFactory.createEmptyBorder());

        // Search bar
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(true);
        top.setBackground(SIDE_BG);
        top.setBorder(new EmptyBorder(12, 10, 8, 10));
        searchField = styledField("Search entries...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { doSearch(); }
            public void removeUpdate(DocumentEvent e)  { doSearch(); }
            public void changedUpdate(DocumentEvent e) { doSearch(); }
        });
        top.add(searchField);
        side.add(top, BorderLayout.NORTH);

        // Entry list — fully opaque, no alpha anywhere
        entryList = new JList<>(listModel);
        entryList.setOpaque(true);
        entryList.setBackground(SIDE_BG);
        entryList.setFont(FONT_BODY);
        entryList.setSelectionBackground(ACCENT);
        entryList.setSelectionForeground(Color.WHITE);
        entryList.setCellRenderer(new EntryRenderer());
        entryList.setFixedCellHeight(58);
        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DiaryEntry sel = entryList.getSelectedValue();
                if (sel != null) loadEntry(sel);
            }
        });

        JScrollPane scroll = new JScrollPane(entryList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(true);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(SIDE_BG);
        scroll.setBackground(SIDE_BG);
        side.add(scroll, BorderLayout.CENTER);
        return side;
    }

    // ── Center tabbed pane ────────────────────────────────────────────────
    private JComponent buildCenter() {
        rightTabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        rightTabs.setOpaque(true);
        rightTabs.setBackground(new Color(0xBDC9E5));
        rightTabs.setFont(FONT_BODY);
        rightTabs.setForeground(new Color(0x4A2D8F));
        rightTabs.setBorder(BorderFactory.createEmptyBorder());

        // Each tab content must be in an opaque, clipping-safe wrapper
        JPanel entryWrapper = new JPanel(new BorderLayout());
        entryWrapper.setOpaque(true);
        entryWrapper.setBackground(new Color(0xBDC9E5));
        entryWrapper.add(buildEntryPanel(), BorderLayout.CENTER);

        JPanel aiWrapper = new JPanel(new BorderLayout());
        aiWrapper.setOpaque(true);
        aiWrapper.setBackground(new Color(0xBDC9E5));
        aiWrapper.add(buildAIPanel(), BorderLayout.CENTER);

        rightTabs.addTab("  Entry  ",        entryWrapper);
        rightTabs.addTab("  AI Assistant  ", aiWrapper);

        // Wrap the tabbed pane itself so it never overflows its BorderLayout cell
        JPanel centerHolder = new JPanel(new BorderLayout());
        centerHolder.setOpaque(true);
        centerHolder.setBackground(new Color(0xBDC9E5));
        centerHolder.add(rightTabs, BorderLayout.CENTER);
        return centerHolder;
    }

    // ── Entry editor panel ────────────────────────────────────────────────
    private JPanel buildEntryPanel() {
        final Color PANEL_BG = new Color(0xBDC9E5);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(PANEL_BG);
        panel.setBorder(new EmptyBorder(14, 18, 8, 18));

        // ---- Title row ----
        JPanel topRow = new JPanel(new BorderLayout(10, 0));
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 14, 0));

        titleField = styledField("Entry title...");
        titleField.setFont(FONT_HEADING);

        String[] moods = {
            "Happy",   "Sad",
            "Angry",   "Calm",
            "Anxious", "Grateful",
            "Tired",   "Excited"
        };
        moodBox = new JComboBox<>(moods);
        moodBox.setFont(FONT_SMALL);
        moodBox.setBackground(Color.WHITE);
        moodBox.setForeground(TEXT_DARK);
        moodBox.setPreferredSize(new Dimension(130, 32));

        JPanel titleRight = new JPanel(new BorderLayout(8, 0));
        titleRight.setOpaque(false);
        titleRight.add(titleField, BorderLayout.CENTER);
        titleRight.add(moodBox,    BorderLayout.EAST);
        topRow.add(titleRight,     BorderLayout.CENTER);

        // ---- Content area ----
        contentArea = new JTextArea();
        contentArea.setFont(FONT_ENTRY);
        contentArea.setBackground(Color.WHITE);
        contentArea.setForeground(new Color(0x1A1040));
        contentArea.setCaretColor(ACCENT);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setBorder(new EmptyBorder(14, 16, 14, 16));
        contentArea.setSelectionColor(new Color(123, 97, 255, 80));
        contentArea.setOpaque(true);

        JScrollPane contentScroll = new JScrollPane(contentArea);
        contentScroll.setBorder(BorderFactory.createEmptyBorder());
        contentScroll.setOpaque(true);
        contentScroll.getViewport().setOpaque(true);

        // ---- Bottom row ----
        JPanel bottomRow = new JPanel(new BorderLayout(0, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(new EmptyBorder(10, 0, 0, 0));

        // Voice controls (left) — fixed height
        JPanel voicePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        voicePanel.setOpaque(false);
        recordBtn    = pill("● Record Voice", ACCENT3);
        playVoiceBtn = pill("► Play Voice",   ACCENT2);
        recordBtn.setForeground(new Color(0x4A2D8F));
        playVoiceBtn.setForeground(new Color(0x4A2D8F));
        voiceLabel   = new JLabel("No voice note");
        voiceLabel.setFont(FONT_SMALL);
        voiceLabel.setForeground(new Color(0x4A2D8F));
        playVoiceBtn.setEnabled(false);
        recordBtn.addActionListener(e    -> toggleRecording());
        playVoiceBtn.addActionListener(e -> playVoice());
        voicePanel.add(recordBtn);
        voicePanel.add(playVoiceBtn);
        voicePanel.add(voiceLabel);

        // Action buttons (right)
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        btnPanel.setOpaque(false);
        deleteBtn            = pill("Delete",        new Color(0xFF6B6B));
        JButton correctBtn   = pill("AI Correct",    new Color(0x4CAF50));
        JButton summarizeBtn = pill("AI Summarize",  ACCENT2);
        saveBtn              = pill("Save Entry",    ACCENT);
        deleteBtn.setForeground(new Color(0x4A2D8F));
        correctBtn.setForeground(new Color(0x4A2D8F));
        summarizeBtn.setForeground(new Color(0x4A2D8F));
        saveBtn.setForeground(new Color(0x4A2D8F));
        deleteBtn.setEnabled(false);
        saveBtn.addActionListener(e      -> saveEntry());
        deleteBtn.addActionListener(e    -> deleteEntry());
        correctBtn.addActionListener(e   -> correctEntry());
        summarizeBtn.addActionListener(e -> summarizeEntry());
        btnPanel.add(deleteBtn);
        btnPanel.add(summarizeBtn);
        btnPanel.add(correctBtn);
        btnPanel.add(saveBtn);

        bottomRow.add(voicePanel, BorderLayout.WEST);
        bottomRow.add(btnPanel,   BorderLayout.EAST);

        panel.add(topRow,        BorderLayout.NORTH);
        panel.add(contentScroll, BorderLayout.CENTER);
        panel.add(bottomRow,     BorderLayout.SOUTH);
        return panel;
    }

    // ── AI chat panel ─────────────────────────────────────────────────────
    private JPanel buildAIPanel() {
        final Color PANEL_BG = new Color(0xBDC9E5);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(true);
        panel.setBackground(PANEL_BG);
        panel.setBorder(new EmptyBorder(14, 18, 8, 18));

        JLabel heading = new JLabel("AI Diary Assistant — Ask me anything");
        heading.setFont(FONT_HEADING);
        heading.setForeground(new Color(0x4A2D8F));
        heading.setBorder(new EmptyBorder(0, 0, 8, 0));

        aiChatArea = new JTextArea();
        aiChatArea.setEditable(false);
        aiChatArea.setFont(FONT_BODY);
        aiChatArea.setBackground(Color.WHITE);
        aiChatArea.setForeground(new Color(0x1A1040));
        aiChatArea.setLineWrap(true);
        aiChatArea.setWrapStyleWord(true);
        aiChatArea.setBorder(new EmptyBorder(10, 12, 10, 12));
        aiChatArea.setOpaque(true);
        aiChatArea.setText(
            "Hi! I am your diary assistant.\n\n" +
            "Try asking me:\n" +
            "  - What should I write about today?\n" +
            "  - Help me summarize my feelings\n" +
            "  - Give me a journal prompt\n" +
            "  - I am feeling anxious, help me reflect\n"
        );

        JScrollPane chatScroll = new JScrollPane(aiChatArea);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.setOpaque(true);
        chatScroll.getViewport().setOpaque(true);

        // Input row
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.setBorder(new EmptyBorder(4, 0, 0, 0));

        aiInputField = styledField("Type your message and press Enter...");
        aiInputField.addActionListener(e -> sendAIMessage());

        JButton sendBtn  = pill("Send",  ACCENT);
        JButton clearBtn = pill("Clear", new Color(0xD0D8EE));
        sendBtn.setForeground(Color.WHITE);
        clearBtn.setForeground(new Color(0x4A2D8F));
        sendBtn.addActionListener(e -> sendAIMessage());
        clearBtn.addActionListener(e -> {
            aiChatArea.setText("Chat cleared.\n");
            ai.clearHistory();
            setStatus("Chat history cleared");
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btns.setOpaque(false);
        btns.add(clearBtn);
        btns.add(sendBtn);

        inputRow.add(aiInputField, BorderLayout.CENTER);
        inputRow.add(btns,         BorderLayout.EAST);

        panel.add(heading,    BorderLayout.NORTH);
        panel.add(chatScroll, BorderLayout.CENTER);
        panel.add(inputRow,   BorderLayout.SOUTH);
        return panel;
    }

    // ── Status bar ────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(true);
        bar.setBackground(new Color(0xB0BCE0));
        bar.setPreferredSize(new Dimension(0, 26));
        bar.setBorder(BorderFactory.createEmptyBorder());

        statusLabel = new JLabel("  Ready");
        statusLabel.setFont(FONT_SMALL);
        statusLabel.setForeground(new Color(0x4A2D8F));

        JLabel cr = new JLabel("Eunoia  ");
        cr.setFont(FONT_SMALL);
        cr.setForeground(new Color(0x4A2D8F));

        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(cr,          BorderLayout.EAST);
        return bar;
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private void newEntry() {
        currentEntry = null;
        editMode     = false;
        titleField.setText("Entry title...");
        titleField.setForeground(new Color(0xAA99CC));
        contentArea.setText("");
        moodBox.setSelectedIndex(0);
        voiceLabel.setText("No voice note");
        playVoiceBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        entryList.clearSelection();
        rightTabs.setSelectedIndex(0);
        titleField.requestFocusInWindow();
        setStatus("New entry — start writing!");
    }

    private void loadEntry(DiaryEntry entry) {
        currentEntry = entry;
        editMode     = true;
        titleField.setText(entry.getTitle());
        titleField.setForeground(new Color(0x1A1040));
        contentArea.setText(entry.getContent());
        deleteBtn.setEnabled(true);

        // Restore mood selection
        String mood = entry.getMood().replaceAll("[^\\x00-\\x7F\\s]", "").trim();
        for (int i = 0; i < moodBox.getItemCount(); i++) {
            if (moodBox.getItemAt(i).equals(mood)) {
                moodBox.setSelectedIndex(i);
                break;
            }
        }

        // Voice note indicator
        String vp = entry.getVoiceNotePath();
        boolean hasVoice = vp != null && new File(vp).exists();
        voiceLabel.setText(hasVoice ? "Voice note saved" : "No voice note");
        playVoiceBtn.setEnabled(hasVoice);

        rightTabs.setSelectedIndex(0);
        setStatus("Opened: " + entry.getTitle());
    }

    private void saveEntry() {
        String title   = titleField.getText().trim();
        String content = contentArea.getText().trim();
        String mood    = (String) moodBox.getSelectedItem();

        if (title.isEmpty() || title.equals("Entry title...")) {
            showMsg("Please enter a title for your entry.");
            return;
        }
        if (content.isEmpty()) {
            showMsg("Please write something before saving!");
            return;
        }

        if (editMode && currentEntry != null) {
            manager.updateEntry(currentEntry.getId(), title, content, mood);
            setStatus("Entry updated ✓");
        } else {
            DiaryEntry entry = new DiaryEntry(title, content, mood);
            manager.addEntry(entry);
            currentEntry = entry;
            editMode     = true;
            deleteBtn.setEnabled(true);
            setStatus("Entry saved ✓");
        }
        refreshList(manager.getAllEntries());
    }

    private void deleteEntry() {
        if (currentEntry == null) return;
        int r = JOptionPane.showConfirmDialog(this,
                "Delete \"" + currentEntry.getTitle() + "\"?\nThis cannot be undone.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) {
            manager.deleteEntry(currentEntry.getId());
            newEntry();
            refreshList(manager.getAllEntries());
            setStatus("Entry deleted");
        }
    }

    private void doSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty() || q.equals("Search entries..."))
            refreshList(manager.getAllEntries());
        else
            refreshList(manager.searchByKeyword(q));
    }

    private void correctEntry() {
        String content = contentArea.getText().trim();
        if (content.isEmpty()) {
            showMsg("Nothing to correct — write something first!");
            return;
        }
        setStatus("AI is correcting your entry...");
        saveBtn.setEnabled(false);

        new Thread(() -> {
            String corrected = ai.correctEntry(content);
            SwingUtilities.invokeLater(() -> {
                saveBtn.setEnabled(true);
                if (corrected.startsWith("API Error") || corrected.startsWith("Connection error")
                        || corrected.startsWith("Rate limit")) {
                    showMsg("AI error: " + corrected);
                    setStatus("Correction failed");
                    return;
                }
                int choice = JOptionPane.showConfirmDialog(this,
                        "AI has corrected your entry. Apply the changes?",
                        "AI Correction", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    contentArea.setText(corrected);
                    setStatus("Entry corrected by AI ✓");
                } else {
                    setStatus("Correction cancelled");
                }
            });
        }).start();
    }

    private void summarizeEntry() {
        String content = contentArea.getText().trim();
        if (content.isEmpty()) {
            showMsg("Nothing to summarize — write something first!");
            return;
        }
        setStatus("AI is summarizing...");

        new Thread(() -> {
            String summary = ai.summarizeEntry(content);
            SwingUtilities.invokeLater(() -> {
                if (summary.startsWith("API Error") || summary.startsWith("Connection error")
                        || summary.startsWith("Rate limit")) {
                    showMsg("AI error: " + summary);
                    setStatus("Summarize failed");
                    return;
                }
                JTextArea ta = new JTextArea(summary);
                ta.setFont(FONT_BODY);
                ta.setLineWrap(true);
                ta.setWrapStyleWord(true);
                ta.setEditable(false);
                ta.setBackground(new Color(0xF0F4FF));
                ta.setBorder(new EmptyBorder(10, 10, 10, 10));
                ta.setSize(420, Integer.MAX_VALUE);
                ta.setPreferredSize(new Dimension(420, ta.getPreferredSize().height));

                JScrollPane sp = new JScrollPane(ta);
                sp.setPreferredSize(new Dimension(440, Math.min(ta.getPreferredSize().height + 20, 300)));
                sp.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xAAAAAA), 1),
                    BorderFactory.createLineBorder(new Color(0xAAAAAA), 1)));

                JPanel spWrapper = new JPanel(new BorderLayout());
                spWrapper.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xAAAAAA), 1),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)));
                spWrapper.add(sp, BorderLayout.CENTER);

                JOptionPane.showMessageDialog(this, spWrapper,
                        "AI Summary", JOptionPane.PLAIN_MESSAGE);
                setStatus("Ready");
            });
        }).start();
    }

    private void toggleRecording() {
        if (recorder.isRecording()) {
            recorder.stopRecording();
            recordBtn.setText("● Record Voice");
            recordBtn.setBackground(new Color(0xEFAFA5));
            String path = recorder.getLastRecordingPath();
            if (currentEntry != null && path != null) {
                manager.setVoiceNote(currentEntry.getId(), path);
                currentEntry.setVoiceNotePath(path);
                voiceLabel.setText("Voice note saved");
                playVoiceBtn.setEnabled(true);
            }
            setStatus("Voice note saved ✓");
        } else {
            if (currentEntry == null) {
                showMsg("Please save the entry first before recording a voice note.");
                return;
            }
            String path = "recordings/" + currentEntry.getId() + ".wav";
            if (recorder.startRecording(path)) {
                recordBtn.setText("■ Stop Recording");
                recordBtn.setBackground(REC_RED);
                setStatus("Recording... click Stop when done");
            } else {
                showMsg("Could not start recording. Please check your microphone.");
            }
        }
    }

    private void playVoice() {
        if (currentEntry == null) return;
        String path = currentEntry.getVoiceNotePath();
        if (path == null || !new File(path).exists()) {
            showMsg("Voice note file not found.");
            return;
        }
        setStatus("Playing voice note...");
        playVoiceBtn.setEnabled(false);
        new Thread(() -> {
            recorder.playRecording(path);
            SwingUtilities.invokeLater(() -> {
                playVoiceBtn.setEnabled(true);
                setStatus("Ready");
            });
        }).start();
    }

    private void toggleMusic() {
        if (music.isPlaying()) {
            music.stopMusic();
            musicLabel.setText("Music: Off");
            setStatus("Music stopped");
        } else {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Select a .wav lofi music file");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "WAV Audio (*.wav)", "wav"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                boolean ok = music.loadAndPlay(fc.getSelectedFile().getAbsolutePath());
                musicLabel.setText(ok ? "Music: On" : "Music: Error");
                if (!ok) showMsg("Could not play file. Make sure it is a valid .wav file.");
                else     setStatus("Lofi music playing");
            }
        }
    }

    private void sendAIMessage() {
        String msg = aiInputField.getText().trim();
        if (msg.isEmpty() || msg.equals("Type your message and press Enter...")) return;

        aiChatArea.append("\nYou: " + msg + "\n");
        aiInputField.setText("");
        aiInputField.setForeground(new Color(0x1A1040));
        setStatus("AI is thinking...");

        new Thread(() -> {
            String reply = ai.chat(msg);
            SwingUtilities.invokeLater(() -> {
                aiChatArea.append("Diary AI: " + reply + "\n");
                aiChatArea.setCaretPosition(aiChatArea.getDocument().getLength());
                setStatus("Ready");
            });
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void refreshList(List<DiaryEntry> list) {
        listModel.clear();
        for (DiaryEntry e : list) listModel.addElement(e);
        int total = manager.getAllEntries().size();
        setStatus("Ready  \u2014  " + total + (total == 1 ? " entry" : " entries"));
    }

    private void setStatus(String msg) {
        statusLabel.setText("  " + msg);
    }

    private void showMsg(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Eunoia", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── Pill button factory ───────────────────────────────────────────────
    private JButton pill(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                // glass shine
                g2.setColor(new Color(255,255,255,40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight()/2, getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
            @Override protected void paintBorder(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255,255,255,80));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, getHeight(), getHeight());
                g2.dispose();
            }
        };
        btn.setFont(FONT_SMALL);
        btn.setBackground(bg);
        btn.setForeground(TEXT_DARK);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setBorder(new EmptyBorder(7, 18, 7, 18));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(bg.brighter());
                btn.repaint();
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(bg);
                btn.repaint();
            }
        });
        return btn;
    }

    // ── Styled text field with placeholder ───────────────────────────────
    private JTextField styledField(String placeholder) {
        JTextField tf = new JTextField();
        tf.setFont(FONT_BODY);
        tf.setOpaque(true);
        tf.setBackground(new Color(0xE8EEF8));
        tf.setForeground(new Color(0x9988BB));
        tf.setCaretColor(new Color(0x4A2D8F));
        tf.setText(placeholder);
        tf.setBorder(new EmptyBorder(6, 10, 6, 10));
        tf.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (tf.getText().equals(placeholder)) {
                    tf.setText("");
                    tf.setForeground(new Color(0x1A1040));
                }
            }
            public void focusLost(FocusEvent e) {
                if (tf.getText().isEmpty()) {
                    tf.setText(placeholder);
                    tf.setForeground(new Color(0x9988BB));
                }
            }
        });
        return tf;
    }

    // ── Entry list cell renderer ──────────────────────────────────────────
    // Uses a single reusable panel (stamp renderer pattern) to avoid Swing
    // painting stale cell panels outside the sidebar bounds.
    private class EntryRenderer implements ListCellRenderer<DiaryEntry> {
        private final JPanel cell  = new JPanel(new BorderLayout(2, 2));
        private final JLabel title = new JLabel();
        private final JLabel meta  = new JLabel();

        EntryRenderer() {
            cell.setOpaque(true);   // opaque=true so it fully covers its row slot
            cell.setBorder(new EmptyBorder(9, 14, 9, 14));
            title.setFont(FONT_BODY.deriveFont(Font.BOLD, 13f));
            meta.setFont(FONT_SMALL);
            cell.add(title, BorderLayout.CENTER);
            cell.add(meta,  BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends DiaryEntry> list,
                DiaryEntry e, int idx, boolean sel, boolean focus) {

            // Background — fully solid, no alpha
            if (sel) {
                cell.setBackground(new Color(0x7B61FF));
            } else {
                cell.setBackground(new Color(0xC2CEE6));
            }

            title.setText(e.getTitle());
            title.setForeground(sel ? Color.WHITE : new Color(0x4A2D8F));

            String dateStr   = e.getFormattedDate();
            if (dateStr.length() > 11) dateStr = dateStr.substring(0, 11);
            String moodClean = e.getMood().replaceAll("[^\\x00-\\x7F\\s]", "").trim();
            meta.setText(moodClean + "   " + dateStr);
            meta.setForeground(sel ? new Color(220, 210, 255) : new Color(0x6644AA));

            return cell;
        }

    }

    // ── Entry point ───────────────────────────────────────────────────────
    public static void main(String[] args) {
        try {
            // Cross-platform LAF — system LAF draws its own white tab borders we can't remove
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Kill every border the TabbedPane LAF tries to paint
        UIManager.put("TabbedPane.contentBorderInsets",  new Insets(0, 0, 0, 0));
        UIManager.put("TabbedPane.tabAreaInsets",         new Insets(2, 2, 0, 2));
        UIManager.put("TabbedPane.tabInsets",             new Insets(4, 10, 4, 10));
        UIManager.put("TabbedPane.contentOpaque",         Boolean.FALSE);
        UIManager.put("TabbedPane.tabsOverlapBorder",     Boolean.TRUE);
        UIManager.put("TabbedPane.shadow",                new Color(0xBDC9E5));
        UIManager.put("TabbedPane.darkShadow",            new Color(0xBDC9E5));
        UIManager.put("TabbedPane.light",                 new Color(0xBDC9E5));
        UIManager.put("TabbedPane.highlight",             new Color(0xBDC9E5));
        UIManager.put("TabbedPane.focus",                 new Color(0xBDC9E5));
        UIManager.put("TabbedPane.borderHightlightColor", new Color(0xBDC9E5));
        UIManager.put("TabbedPane.background",            new Color(0xBDC9E5));

        UIManager.put("Button.focus",  new Color(0xBDC9E5, true));
        UIManager.put("Button.select", new Color(0xBDC9E5));
        SwingUtilities.invokeLater(DiaryApp::new);
    }
}