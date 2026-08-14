package com.asmr.tools;

import android.app.Activity;
import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;
import androidx.core.content.FileProvider;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int REQUEST_OPEN_TREE = 1001;
    private static final int REQUEST_OPEN_FILES = 1002;
    private static final int REQUEST_WRITE_STORAGE = 1003;

    private static final int MODE_SUBTITLE = 0;
    private static final int MODE_AUDIO = 1;

    private static final String PREFS_NAME = "asmr_tools_prefs";
    private static final String PREF_FOLDERS = "folders";
    private static final String PREF_FILES = "files";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Uri> folderUris = new ArrayList<>();
    private final List<Uri> fileUris = new ArrayList<>();
    private final List<ResultItem> resultItems = new ArrayList<>();

    private int currentMode = MODE_SUBTITLE;
    private boolean pendingOpenFilePicker = false;

    private RadioGroup rgTimeFormat;
    private Spinner spinnerBitrate;

    private TextView tvTitle;
    private TextView tvSubtitleHint;
    private TextView tvAudioHint;
    private TextView tvSelection;
    private TextView tvStatus;
    private TextView tvLog;
    private TextView tvResults;
    private TextView tvSubtitleModeLabel;
    private TextView tvAudioModeLabel;

    private ImageView imgSubtitle;
    private ImageView imgAudio;

    private LinearLayout layoutSubtitleSettings;
    private LinearLayout layoutAudioSettings;
    private LinearLayout modeSubtitle;
    private LinearLayout modeAudio;

    private Button btnAddFile;
    private Button btnAddFolder;
    private Button btnClearSelection;
    private Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rgTimeFormat = findViewById(R.id.rg_time_format);
        spinnerBitrate = findViewById(R.id.spinner_bitrate);
        tvTitle = findViewById(R.id.tv_title);
        tvSubtitleHint = findViewById(R.id.tv_subtitle_hint);
        tvAudioHint = findViewById(R.id.tv_audio_hint);
        tvSelection = findViewById(R.id.tv_selection);
        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        tvResults = findViewById(R.id.tv_results);
        tvSubtitleModeLabel = findViewById(R.id.tv_subtitle_mode_label);
        tvAudioModeLabel = findViewById(R.id.tv_audio_mode_label);
        imgSubtitle = findViewById(R.id.img_subtitle);
        imgAudio = findViewById(R.id.img_audio);
        layoutSubtitleSettings = findViewById(R.id.layout_subtitle_settings);
        layoutAudioSettings = findViewById(R.id.layout_audio_settings);
        modeSubtitle = findViewById(R.id.mode_subtitle);
        modeAudio = findViewById(R.id.mode_audio);
        btnAddFile = findViewById(R.id.btn_add_file);
        btnAddFolder = findViewById(R.id.btn_add_folder);
        btnClearSelection = findViewById(R.id.btn_clear_selection);
        btnStart = findViewById(R.id.btn_start);

        ArrayAdapter<String> bitrateAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"320 kbps（默认）", "256 kbps", "192 kbps", "128 kbps"});
        bitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBitrate.setAdapter(bitrateAdapter);

        btnAddFile.setOnClickListener(v -> openFilePicker());
        btnAddFolder.setOnClickListener(v -> openFolderPicker());
        btnClearSelection.setOnClickListener(v -> clearSelection());
        btnStart.setOnClickListener(v -> startBatch());
        modeSubtitle.setOnClickListener(v -> setMode(MODE_SUBTITLE));
        modeAudio.setOnClickListener(v -> setMode(MODE_AUDIO));
        tvResults.setMovementMethod(LinkMovementMethod.getInstance());

        loadSelections();
        setMode(MODE_SUBTITLE);
        updateSelectionDisplay();
        renderResults();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingOpenFilePicker && hasAllFilesAccess()) {
            pendingOpenFilePicker = false;
            openFilePicker();
        }
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_OPEN_TREE);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件夹选择器：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openFilePicker() {
        if (!hasAllFilesAccess()) {
            pendingOpenFilePicker = true;
            Toast.makeText(this, "请先授予“所有文件访问”权限，才能写入源目录。", Toast.LENGTH_LONG).show();
            openAllFilesAccessSettings();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (currentMode == MODE_AUDIO) {
            intent.setType("audio/*");
        } else {
            intent.setType("*/*");
        }
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_OPEN_FILES);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception ignored) {
                    Toast.makeText(this, "请手动打开系统设置，授予所有文件访问权限。", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingOpenFilePicker = true;
                openFilePicker();
            } else {
                Toast.makeText(this, "未授予存储权限，无法直接写入源目录。", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQUEST_OPEN_TREE) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                addFolderUri(treeUri);
                updateSelectionDisplay();
                appendLog("已添加文件夹：" + describeUri(treeUri));
            }
        } else if (requestCode == REQUEST_OPEN_FILES) {
            ClipData clipData = data.getClipData();
            int added = 0;
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    if (uri != null && addFileUri(uri)) {
                        added++;
                    }
                }
            } else {
                Uri uri = data.getData();
                if (uri != null && addFileUri(uri)) {
                    added++;
                }
            }
            if (added > 0) {
                saveFiles();
                updateSelectionDisplay();
                appendLog("已添加文件：" + added + " 个");
            }
        }
    }

    private boolean addFileUri(Uri uri) {
        if (fileUris.contains(uri)) {
            return false;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        fileUris.add(uri);
        return true;
    }

    private void addFolderUri(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        if (!folderUris.contains(uri)) {
            folderUris.add(uri);
            saveFolders();
        }
    }

    private void loadSelections() {
        loadFolders();
        loadFiles();
    }

    private void loadFolders() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(PREF_FOLDERS, "");
        if (saved == null || saved.trim().isEmpty()) {
            return;
        }
        for (String item : saved.split("\\n")) {
            if (item != null && !item.trim().isEmpty()) {
                try {
                    folderUris.add(Uri.parse(item));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void loadFiles() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(PREF_FILES, "");
        if (saved == null || saved.trim().isEmpty()) {
            return;
        }
        for (String item : saved.split("\\n")) {
            if (item != null && !item.trim().isEmpty()) {
                try {
                    fileUris.add(Uri.parse(item));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void saveFolders() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_FOLDERS, joinUris(folderUris))
                .apply();
    }

    private void saveFiles() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_FILES, joinUris(fileUris))
                .apply();
    }

    private String joinUris(List<Uri> uris) {
        StringBuilder sb = new StringBuilder();
        for (Uri uri : uris) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(uri.toString());
        }
        return sb.toString();
    }

    private void clearSelection() {
        folderUris.clear();
        fileUris.clear();
        saveFolders();
        saveFiles();
        updateSelectionDisplay();
        appendLog("已清空文件/文件夹列表。");
    }

    private void updateSelectionDisplay() {
        if (fileUris.isEmpty() && folderUris.isEmpty()) {
            tvSelection.setText("尚未选择文件或文件夹");
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (!fileUris.isEmpty()) {
            sb.append("文件（").append(fileUris.size()).append(" 个）：\n");
            appendUriList(sb, fileUris, 5);
        }
        if (!folderUris.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("文件夹（").append(folderUris.size()).append(" 个）：\n");
            appendUriList(sb, folderUris, 5);
        }
        tvSelection.setText(sb.toString().trim());
    }

    private void appendUriList(StringBuilder sb, List<Uri> uris, int limit) {
        int count = Math.min(limit, uris.size());
        for (int i = 0; i < count; i++) {
            sb.append("• ").append(describeUri(uris.get(i))).append('\n');
        }
        if (uris.size() > count) {
            sb.append("…等 ").append(uris.size() - count).append(" 个\n");
        }
    }

    private String describeUri(Uri uri) {
        String s = uri.toString();
        if (s.contains("%3A")) {
            s = s.replace("%3A", ":");
        }
        return s;
    }

    private void setMode(int mode) {
        currentMode = mode;
        if (mode == MODE_SUBTITLE) {
            tvTitle.setText("字幕转换");
            tvSubtitleHint.setVisibility(View.VISIBLE);
            tvAudioHint.setVisibility(View.GONE);
            layoutSubtitleSettings.setVisibility(View.VISIBLE);
            layoutAudioSettings.setVisibility(View.GONE);
            btnStart.setText("开始字幕转换");

            modeSubtitle.setBackgroundResource(R.drawable.bg_mode_selected);
            modeAudio.setBackgroundResource(R.drawable.bg_mode_unselected);
            tvSubtitleModeLabel.setTextColor(getColor(R.color.primary_dark));
            tvAudioModeLabel.setTextColor(getColor(R.color.text_muted));
            imgSubtitle.setColorFilter(getColor(R.color.primary_dark));
            imgAudio.setColorFilter(getColor(R.color.text_muted));
        } else {
            tvTitle.setText("音频转换");
            tvSubtitleHint.setVisibility(View.GONE);
            tvAudioHint.setVisibility(View.VISIBLE);
            layoutSubtitleSettings.setVisibility(View.GONE);
            layoutAudioSettings.setVisibility(View.VISIBLE);
            btnStart.setText("开始音频转换");

            modeAudio.setBackgroundResource(R.drawable.bg_mode_selected);
            modeSubtitle.setBackgroundResource(R.drawable.bg_mode_unselected);
            tvAudioModeLabel.setTextColor(getColor(R.color.primary_dark));
            tvSubtitleModeLabel.setTextColor(getColor(R.color.text_muted));
            imgAudio.setColorFilter(getColor(R.color.primary_dark));
            imgSubtitle.setColorFilter(getColor(R.color.text_muted));
        }
    }

    private void startBatch() {
        if (fileUris.isEmpty() && folderUris.isEmpty()) {
            String hint = currentMode == MODE_SUBTITLE ? "请先添加字幕文件或文件夹。" : "请先添加音频文件或文件夹。";
            Toast.makeText(this, hint, Toast.LENGTH_LONG).show();
            return;
        }

        setControlsEnabled(false);
        tvStatus.setText("状态：处理中…");
        tvLog.setText("日志：");
        resultItems.clear();
        renderResults();

        boolean hhmmss = rgTimeFormat.getCheckedRadioButtonId() == R.id.radio_hh_mm_ss;
        int bitrate = getSelectedBitrate();
        int mode = currentMode;

        Thread worker = new Thread(() -> runBatch(mode, hhmmss, bitrate));
        worker.setName("asmr-batch-worker");
        worker.start();
    }

    private int getSelectedBitrate() {
        int pos = spinnerBitrate.getSelectedItemPosition();
        switch (pos) {
            case 1:
                return 256;
            case 2:
                return 192;
            case 3:
                return 128;
            case 0:
            default:
                return 320;
        }
    }

    private void setControlsEnabled(boolean enabled) {
        btnAddFile.setEnabled(enabled);
        btnAddFolder.setEnabled(enabled);
        btnClearSelection.setEnabled(enabled);
        btnStart.setEnabled(enabled);
        modeSubtitle.setEnabled(enabled);
        modeAudio.setEnabled(enabled);
    }

    private void runBatch(int mode, boolean hhmmss, int bitrate) {
        int[] counts = new int[3];
        List<Uri> files = new ArrayList<>(fileUris);
        List<Uri> folders = new ArrayList<>(folderUris);

        for (Uri uri : files) {
            boolean handledAsRaw = false;
            if (hasAllFilesAccess()) {
                String path = getFilePathFromUri(uri);
                if (path != null) {
                    File rawFile = new File(path);
                    if (rawFile.isFile()) {
                        processRawFile(rawFile, mode, hhmmss, bitrate, counts);
                        handledAsRaw = true;
                    }
                }
            }
            if (!handledAsRaw) {
                DocumentFile file = DocumentFile.fromSingleUri(this, uri);
                if (file == null || !file.isFile()) {
                    postLog("跳过无法读取的文件：" + describeUri(uri));
                    continue;
                }
                processDocument(file, mode, hhmmss, bitrate, counts);
            }
        }

        for (Uri uri : folders) {
            DocumentFile root = DocumentFile.fromTreeUri(this, uri);
            if (root == null || !root.canRead()) {
                postLog("跳过无法读取的文件夹：" + describeUri(uri));
                continue;
            }
            processDirectory(root, mode, hhmmss, bitrate, counts);
        }

        String type = mode == MODE_SUBTITLE ? "VTT→LRC" : "WAV→MP3";
        String summary = "完成：" + type + " " + counts[1] + "/" + counts[0]
                + "，失败 " + counts[2] + "。";
        mainHandler.post(() -> {
            setControlsEnabled(true);
            tvStatus.setText(counts[2] == 0 ? "状态：全部完成" : "状态：完成，但有失败项");
            appendLog(summary);
        });
    }

    private void processDirectory(DocumentFile directory, int mode, boolean hhmmss, int bitrate, int[] counts) {
        DocumentFile[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                processDirectory(child, mode, hhmmss, bitrate, counts);
            } else if (child.isFile()) {
                processDocument(child, mode, hhmmss, bitrate, counts);
            }
        }
    }

    private void processDocument(DocumentFile file, int mode, boolean hhmmss, int bitrate, int[] counts) {
        String name = file.getName();
        if (name == null) {
            return;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (mode == MODE_SUBTITLE && lower.endsWith(".vtt")) {
            counts[0]++;
            if (processVtt(file, hhmmss)) {
                counts[1]++;
            } else {
                counts[2]++;
            }
        } else if (mode == MODE_AUDIO && lower.endsWith(".wav")) {
            counts[0]++;
            if (processWav(file, bitrate)) {
                counts[1]++;
            } else {
                counts[2]++;
            }
        }
    }

    private boolean processVtt(DocumentFile input, boolean hhmmss) {
        String name = input.getName();
        postLog("转换 VTT：" + name);
        String text;
        try (InputStream in = getContentResolver().openInputStream(input.getUri())) {
            if (in == null) {
                postLog("  失败：无法读取文件。");
                return false;
            }
            text = new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            postLog("  失败：" + e.getMessage());
            return false;
        }

        List<Cue> cues = parseVtt(text);
        if (cues.isEmpty()) {
            postLog("  失败：未解析到有效字幕。");
            return false;
        }

        StringBuilder lrc = new StringBuilder();
        for (Cue cue : cues) {
            String start = formatTime(cue.startMs, hhmmss);
            String end = formatTime(cue.endMs, hhmmss);
            for (String line : cue.lines) {
                lrc.append('[').append(start).append(']').append(line).append('\n');
                lrc.append('[').append(end).append("]\n");
            }
        }

        String outName = stripExtension(name) + ".lrc";
        boolean[] fallback = new boolean[1];
        try (OutputStream os = openOutputForInput(input, outName, "text/plain", fallback)) {
            os.write(lrc.toString().getBytes(StandardCharsets.UTF_8));
            if (fallback[0]) {
                addResultFile(new File(getFallbackOutputDir(), outName), "text/plain");
                postLog("  已保存到应用输出目录：" + outName);
            } else {
                addResultDocument(input, outName, "text/plain");
            }
            postLog("  成功：" + outName);
            return true;
        } catch (Exception e) {
            postLog("  失败：" + e.getMessage());
            return false;
        }
    }

    private boolean processWav(DocumentFile input, int bitrate) {
        String name = input.getName();
        postLog("转换 WAV：" + name);

        File tempInput = new File(getCacheDir(), "wav_in_" + System.currentTimeMillis() + ".wav");
        File tempOutput = new File(getCacheDir(), "mp3_out_" + System.currentTimeMillis() + ".mp3");
        try {
            try (InputStream in = getContentResolver().openInputStream(input.getUri());
                 OutputStream out = new FileOutputStream(tempInput)) {
                if (in == null) {
                    postLog("  失败：无法读取文件。");
                    return false;
                }
                copy(in, out);
            }

            String[] arguments = new String[]{
                    "-y",
                    "-i", tempInput.getAbsolutePath(),
                    "-codec:a", "libmp3lame",
                    "-b:a", bitrate + "k",
                    tempOutput.getAbsolutePath()
            };
            FFmpegSession session = FFmpegKit.executeWithArguments(arguments);
            if (!ReturnCode.isSuccess(session.getReturnCode())) {
                String out = session.getOutput();
                if (out == null) {
                    out = session.getAllLogsAsString();
                }
                postLog("  失败：FFmpeg 返回错误。\n" + truncateForLog(out));
                return false;
            }
            if (!tempOutput.exists() || tempOutput.length() == 0) {
                postLog("  失败：未生成 MP3 文件。");
                return false;
            }

            String outName = stripExtension(name) + ".mp3";
            boolean[] fallback = new boolean[1];
            try (InputStream in = new FileInputStream(tempOutput);
                 OutputStream os = openOutputForInput(input, outName, "audio/mpeg", fallback)) {
                copy(in, os);
                if (fallback[0]) {
                    addResultFile(new File(getFallbackOutputDir(), outName), "audio/mpeg");
                    postLog("  已保存到应用输出目录：" + outName);
                } else {
                    addResultDocument(input, outName, "audio/mpeg");
                }
            }
            postLog("  成功：" + outName);
            return true;
        } catch (Exception e) {
            postLog("  失败：" + e.getMessage());
            return false;
        } finally {
            if (tempInput.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempInput.delete();
            }
            if (tempOutput.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempOutput.delete();
            }
        }
    }

    private void processRawFile(File input, int mode, boolean hhmmss, int bitrate, int[] counts) {
        String name = input.getName();
        if (name == null) {
            return;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (mode == MODE_SUBTITLE && lower.endsWith(".vtt")) {
            counts[0]++;
            if (processRawVtt(input, hhmmss)) {
                counts[1]++;
            } else {
                counts[2]++;
            }
        } else if (mode == MODE_AUDIO && lower.endsWith(".wav")) {
            counts[0]++;
            if (processRawWav(input, bitrate)) {
                counts[1]++;
            } else {
                counts[2]++;
            }
        }
    }

    private boolean processRawVtt(File input, boolean hhmmss) {
        postLog("转换 VTT：" + input.getAbsolutePath());
        try (InputStream in = new FileInputStream(input)) {
            String text = new String(readAll(in), StandardCharsets.UTF_8);
            List<Cue> cues = parseVtt(text);
            if (cues.isEmpty()) {
                postLog("  失败：未解析到有效字幕。");
                return false;
            }

            StringBuilder lrc = new StringBuilder();
            for (Cue cue : cues) {
                String start = formatTime(cue.startMs, hhmmss);
                String end = formatTime(cue.endMs, hhmmss);
                for (String line : cue.lines) {
                    lrc.append('[').append(start).append(']').append(line).append('\n');
                    lrc.append('[').append(end).append("]\n");
                }
            }

            File out = new File(input.getParentFile(), stripExtension(input.getName()) + ".lrc");
            try (OutputStream os = new FileOutputStream(out)) {
                os.write(lrc.toString().getBytes(StandardCharsets.UTF_8));
            }
            addResultFile(out, "text/plain");
            postLog("  成功：" + out.getAbsolutePath());
            return true;
        } catch (Exception e) {
            postLog("  失败：" + e.getMessage());
            return false;
        }
    }

    private boolean processRawWav(File input, int bitrate) {
        postLog("转换 WAV：" + input.getAbsolutePath());
        File out = new File(input.getParentFile(), stripExtension(input.getName()) + ".mp3");
        try {
            String[] arguments = new String[]{
                    "-y",
                    "-i", input.getAbsolutePath(),
                    "-codec:a", "libmp3lame",
                    "-b:a", bitrate + "k",
                    out.getAbsolutePath()
            };
            FFmpegSession session = FFmpegKit.executeWithArguments(arguments);
            if (!ReturnCode.isSuccess(session.getReturnCode())) {
                String log = session.getOutput();
                if (log == null) {
                    log = session.getAllLogsAsString();
                }
                postLog("  失败：FFmpeg 返回错误。\n" + truncateForLog(log));
                return false;
            }
            if (!out.exists() || out.length() == 0) {
                postLog("  失败：未生成 MP3 文件。");
                return false;
            }
            addResultFile(out, "audio/mpeg");
            postLog("  成功：" + out.getAbsolutePath());
            return true;
        } catch (Exception e) {
            postLog("  失败：" + e.getMessage());
            return false;
        }
    }

    private String getFilePathFromUri(Uri uri) {
        if (uri == null) {
            return null;
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && DocumentsContract.isDocumentUri(this, uri)) {
            String authority = uri.getAuthority();
            String docId = DocumentsContract.getDocumentId(uri);
            if (docId != null && docId.startsWith("raw:")) {
                return docId.substring(4);
            }
            if ("com.android.externalstorage.documents".equals(authority) && docId != null) {
                String[] split = docId.split(":", 2);
                if (split.length == 2) {
                    String volume = split[0];
                    String relative = split[1];
                    if ("primary".equalsIgnoreCase(volume)) {
                        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + relative;
                    }
                    return "/storage/" + volume + "/" + relative;
                }
            }
        }
        return null;
    }

    private File getFallbackOutputDir() {
        File base = getExternalFilesDir(null);
        if (base == null) {
            base = getFilesDir();
        }
        File dir = new File(base, "ASMR输出");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    private void addResultFile(File file, String mimeType) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            addResultItem(file.getName(), uri, mimeType);
        } catch (Exception e) {
            postLog("无法生成查看链接：" + e.getMessage());
        }
    }

    private void addResultDocument(DocumentFile input, String outName, String mimeType) {
        DocumentFile parent = input.getParentFile();
        if (parent == null) {
            return;
        }
        DocumentFile out = parent.findFile(outName);
        if (out != null) {
            addResultItem(outName, out.getUri(), mimeType);
        }
    }

    private void addResultItem(String label, Uri uri, String mimeType) {
        mainHandler.post(() -> {
            resultItems.add(new ResultItem(label, uri, mimeType));
            if (resultItems.size() > 50) {
                resultItems.subList(0, resultItems.size() - 50).clear();
            }
            renderResults();
        });
    }

    private void renderResults() {
        if (resultItems.isEmpty()) {
            tvResults.setText("");
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("转换结果（点击查看）：\n");
        for (ResultItem item : resultItems) {
            int start = sb.length();
            sb.append("• ").append(item.label).append("\n");
            int end = sb.length();
            sb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    openViewUri(item.uri, item.mimeType);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(getColor(R.color.primary_dark)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvResults.setText(sb);
    }

    private void openViewUri(Uri uri, String mimeType) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "查看文件"));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private OutputStream openOutputForInput(DocumentFile input, String outName, String mimeType, boolean[] fallback) throws Exception {
        DocumentFile parent = input.getParentFile();
        if (parent != null) {
            try {
                DocumentFile out = createOutputDocument(parent, outName, mimeType);
                if (out != null) {
                    OutputStream os = getContentResolver().openOutputStream(out.getUri(), "w");
                    if (os != null) {
                        return os;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        File base = getExternalFilesDir(null);
        if (base == null) {
            base = getFilesDir();
        }
        File dir = new File(base, "ASMR输出");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        File file = new File(dir, outName);
        fallback[0] = true;
        return new FileOutputStream(file);
    }

    private DocumentFile createOutputDocument(DocumentFile parent, String outName, String mimeType) {
        if (parent == null) {
            return null;
        }
        DocumentFile existing = parent.findFile(outName);
        if (existing != null) {
            existing.delete();
        }
        return parent.createFile(mimeType, outName);
    }

    private List<Cue> parseVtt(String raw) {
        List<Cue> cues = new ArrayList<>();
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] blocks = normalized.split("\\n\\s*\\n");
        boolean firstBlock = true;
        for (String block : blocks) {
            if (block.trim().isEmpty()) {
                continue;
            }
            if (firstBlock) {
                firstBlock = false;
                if (block.trim().toUpperCase(Locale.ROOT).startsWith("WEBVTT")) {
                    continue;
                }
            }
            String[] lines = block.split("\\n");
            int timingIndex = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("-->")) {
                    timingIndex = i;
                    break;
                }
            }
            if (timingIndex < 0) {
                continue;
            }
            String timing = lines[timingIndex].trim();
            String[] parts = timing.split("-->");
            if (parts.length < 2) {
                continue;
            }
            long start = parseVttTimestamp(parts[0].trim());
            String endPart = parts[1].trim();
            int space = endPart.indexOf(' ');
            if (space >= 0) {
                endPart = endPart.substring(0, space);
            }
            long end = parseVttTimestamp(endPart.trim());
            if (start < 0 || end < 0 || end < start) {
                continue;
            }

            List<String> payload = new ArrayList<>();
            for (int i = timingIndex + 1; i < lines.length; i++) {
                String cleaned = cleanVttLine(lines[i]);
                if (!cleaned.isEmpty()) {
                    payload.add(cleaned);
                }
            }
            if (payload.isEmpty()) {
                continue;
            }
            cues.add(new Cue(start, end, payload));
        }
        return cues;
    }

    private String cleanVttLine(String line) {
        String s = line.trim();
        s = s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");

        Matcher vMatcher = Pattern.compile("<v([^>]*)>").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (vMatcher.find()) {
            String label = vMatcher.group(1).trim();
            String replacement = label.isEmpty() ? "" : label + ": ";
            vMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        vMatcher.appendTail(sb);
        s = sb.toString();
        s = s.replaceAll("<[^>]+>", "");
        return s.trim();
    }

    private long parseVttTimestamp(String s) {
        s = s.trim();
        String mainPart = s;
        String msPart = "000";
        int dot = s.lastIndexOf('.');
        int comma = s.lastIndexOf(',');
        int sep = Math.max(dot, comma);
        if (sep >= 0) {
            mainPart = s.substring(0, sep);
            msPart = s.substring(sep + 1);
        }
        String[] hms = mainPart.split(":");
        if (hms.length != 2 && hms.length != 3) {
            return -1;
        }
        try {
            long hours = 0;
            long minutes;
            long seconds;
            if (hms.length == 3) {
                hours = Long.parseLong(hms[0].trim());
                minutes = Long.parseLong(hms[1].trim());
                seconds = Long.parseLong(hms[2].trim());
            } else {
                minutes = Long.parseLong(hms[0].trim());
                seconds = Long.parseLong(hms[1].trim());
            }
            while (msPart.length() < 3) {
                msPart += "0";
            }
            if (msPart.length() > 3) {
                msPart = msPart.substring(0, 3);
            }
            long millis = Long.parseLong(msPart);
            return ((hours * 60L + minutes) * 60L + seconds) * 1000L + millis;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String formatTime(long ms, boolean hhmmss) {
        if (ms < 0) {
            ms = 0;
        }
        long hours = ms / 3600000L;
        long minutes = (ms / 60000L) % 60L;
        long seconds = (ms / 1000L) % 60L;
        long millis = ms % 1000L;
        if (hhmmss) {
            return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        }
        long totalMinutes = ms / 60000L;
        return String.format(Locale.US, "%02d:%02d.%03d", totalMinutes, seconds, millis);
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    private byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        out.flush();
    }

    private String truncateForLog(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= 1200) {
            return s;
        }
        return s.substring(0, 1200) + "\n…";
    }

    private void postLog(String message) {
        mainHandler.post(() -> appendLog(message));
    }

    private void appendLog(String message) {
        String current = tvLog.getText().toString();
        String next = current + "\n" + message;
        if (next.length() > 20000) {
            next = next.substring(next.length() - 20000);
        }
        tvLog.setText(next);
    }

    private static class Cue {
        final long startMs;
        final long endMs;
        final List<String> lines;

        Cue(long startMs, long endMs, List<String> lines) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.lines = lines;
        }
    }

    private static class ResultItem {
        final String label;
        final Uri uri;
        final String mimeType;

        ResultItem(String label, Uri uri, String mimeType) {
            this.label = label;
            this.uri = uri;
            this.mimeType = mimeType;
        }
    }
}
