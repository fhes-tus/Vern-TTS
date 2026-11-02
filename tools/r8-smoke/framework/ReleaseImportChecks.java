package com.veritas.reader.releasechecks;

import android.app.Activity;
import android.app.Instrumentation;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Framework-only instrumentation: no app symbols or extra target keep rules. */
public final class ReleaseImportChecks extends Instrumentation {
    private static final String TARGET = "com.veritas.reader.r8.harness.checks";
    private Bundle args;
    private Context context;
    private interface Check { boolean ready() throws Exception; }

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments == null ? new Bundle() : arguments;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            context = getTargetContext();
            require(TARGET.equals(context.getPackageName()), "Isolated target only");
            context.getSharedPreferences("veritas_reader_library", 0).edit()
                .putString("user_name", "Release check")
                .putBoolean("onboarding_tutorial_seen", true)
                .putBoolean("battery_unrestricted_never_ask", true).commit();
            if ("true".equals(args.getString("verifyUpgrade"))) {
                require(context.getSharedPreferences("release_import_checks", 0)
                    .getBoolean("importsPassed", false), "Retained import marker missing");
                require(documentCount() >= 3, "Imported documents lost across APK replacement");
                String retainedIds = args.getString("retainedIds", "");
                if (!retainedIds.isEmpty()) {
                    for (String id : retainedIds.split(",")) {
                        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath("veritas_reader.db").getPath(), null, SQLiteDatabase.OPEN_READONLY);
                             Cursor row = db.rawQuery("SELECT file_name FROM documents WHERE id = ?", new String[] {id})) {
                            require(row.moveToFirst(), "Retained document missing: " + id);
                            File file = new File(context.getFilesDir(), "reader_documents/" + row.getString(0));
                            require(file.isFile() && file.length() > 0, "Retained content missing: " + id);
                        }
                    }
                    report("PASS retained fixture document IDs and content files");
                }
                report("PASS upgrade retained marker and documents");
            }
            Class.forName("androidx.work.impl.WorkDatabase_Impl").getConstructor().newInstance();
            for (String merger : new String[] {"OverwritingInputMerger", "ArrayCreatingInputMerger"}) {
                Class.forName("androidx.work." + merger).getConstructor().newInstance();
            }
            Class<?> parameters = Class.forName("androidx.work.WorkerParameters");
            for (String worker : new String[] {"DocumentImportWorker", "AutoBackupWorker", "StreakReminderWorker"}) {
                Class.forName("com.veritas.reader." + worker).getConstructor(Context.class, parameters);
            }
            Activity activity = startActivitySync(new Intent().setClassName(TARGET, "com.veritas.reader.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            require(!activity.isFinishing() && !activity.isDestroyed(), "Cold activity failed");
            report("PASS startup, Room, input merger and worker constructors");
            String nonce = Long.toString(SystemClock.elapsedRealtime());
            int completedImports = 0;
            for (String kind : args.getString("formats", "txt,pdf,image").split(",")) {
                if (!"none".equals(kind.trim())) { importDocument(kind.trim(), nonce); completedImports++; }
            }
            if ("true".equals(args.getString("verifyWidget"))) verifyWidget();
            if (completedImports == 3) context.getSharedPreferences("release_import_checks", 0).edit().putBoolean("importsPassed", true).commit();
            result.putString("stream", "PASS release checks; import formats completed: " + completedImports + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Log.e("ReleaseImportChecks", "Release verification failed", failure);
            result.putString("stream", "FAIL " + Log.getStackTraceString(failure) + "\nUI: " + uiText());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyWidget() throws Exception {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        AppWidgetHost host = new AppWidgetHost(context, 0x5253);
        int id = host.allocateAppWidgetId();
        AppWidgetHostView[] view = new AppWidgetHostView[1];
        try {
            getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
            try {
                require(manager.bindAppWidgetIdIfAllowed(id, new ComponentName(TARGET,
                    "com.veritas.reader.ReadingProgressWidgetReceiver")), "Cannot bind isolated Reading Tracker");
            } finally { getUiAutomation().dropShellPermissionIdentity(); }
            runOnMainSync(() -> {
                host.startListening();
                view[0] = host.createView(context, id, manager.getAppWidgetInfo(id));
            });
            Bundle options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 300);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 200);
            manager.updateAppWidgetOptions(id, options);
            context.sendBroadcast(new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(new ComponentName(TARGET, "com.veritas.reader.ReadingProgressWidgetReceiver"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {id}));
            await("Reading Tracker rendered by its worker", 60_000, () -> {
                boolean[] found = new boolean[1];
                runOnMainSync(() -> found[0] = hasText(view[0], "Reading Tracker"));
                return found[0];
            });
            report("PASS Reading Tracker RemoteViews rendered through the Glance worker");
        } finally { runOnMainSync(() -> { host.stopListening(); host.deleteHost(); }); }
    }
    private boolean hasText(View view, String expected) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(expected)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), expected)) return true;
        }
        return false;
    }

    private void importDocument(String kind, String nonce) throws Exception {
        Set<String> oldWorkers = importWorkers();
        File input;
        String mime;
        String expected = "Release import verification " + kind + " " + nonce;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(28f);
        paint.setColor(Color.BLACK);
        switch (kind) {
            case "txt":
                input = new File(context.getCacheDir(), "release-" + nonce + ".txt");
                Files.write(input.toPath(), (expected + ". Complete text remains available.").getBytes(StandardCharsets.UTF_8));
                mime = "text/plain";
                break;
            case "pdf":
                input = new File(context.getCacheDir(), "release-" + nonce + ".pdf");
                PdfDocument pdf = new PdfDocument();
                try {
                    PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(900, 1100, 1).create());
                    for (int line = 0; line < 8; line++) page.getCanvas().drawText(expected + ". Line " + line, 35, 85 + 55 * line, paint);
                    pdf.finishPage(page);
                    try (FileOutputStream output = new FileOutputStream(input)) { pdf.writeTo(output); }
                } finally { pdf.close(); }
                mime = "application/pdf";
                break;
            case "image":
                input = new File(context.getCacheDir(), "release-" + nonce + ".png");
                Bitmap bitmap = Bitmap.createBitmap(1200, 500, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);
                paint.setTextSize(40f);
                canvas.drawText(expected, 40, 120, paint);
                canvas.drawText("Clear text from an image.", 40, 230, paint);
                try (FileOutputStream output = new FileOutputStream(input)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
                bitmap.recycle();
                mime = "image/png";
                break;
            default: throw new IllegalArgumentException("Unknown format " + kind);
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, input.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        require(uri != null, "Cannot create fixture URI");
        try {
            try (java.io.OutputStream output = context.getContentResolver().openOutputStream(uri)) {
                Files.copy(input.toPath(), output);
            }
            Intent share = new Intent(Intent.ACTION_SEND).setClassName(TARGET, "com.veritas.reader.MainActivity")
                .setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newRawUri("Release fixture", uri));
            context.startActivity(share);
            click("Import to Read");
            click("Import");
            await("worker completion for " + kind, 120_000, () -> {
                File database = workDatabase();
                if (!database.isFile()) return false;
                try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                     Cursor rows = db.rawQuery("SELECT id,state,output FROM workspec WHERE worker_class_name = ?",
                         new String[] {"com.veritas.reader.DocumentImportWorker"})) {
                    while (rows.moveToNext()) {
                        if (oldWorkers.contains(rows.getString(0))) continue;
                        int state = rows.getInt(1);
                        if (state == 3 || state == 5) throw new IllegalStateException("Import " + kind + " state=" + state + ": " + data(rows.getBlob(2)));
                        if (state == 2) {
                            report("Worker " + kind + " SUCCEEDED: " + data(rows.getBlob(2)));
                            return true;
                        }
                    }
                    return false;
                }
            });
            boolean found = false;
            File[] extracted = new File(context.getFilesDir(), "reader_documents").listFiles();
            if (extracted != null) for (File file : extracted) {
                if (file.getName().endsWith(".txt") && new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains(expected)) {
                    found = true;
                    try (SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath("veritas_reader.db").getPath(), null, SQLiteDatabase.OPEN_READONLY);
                         Cursor row = db.rawQuery("SELECT id FROM documents WHERE file_name = ?", new String[] {file.getName()})) {
                        require(row.moveToFirst(), "Persisted document row missing for " + kind);
                    }
                    break;
                }
            }
            require(found, "Worker succeeded without expected extracted content for " + kind);
            report("PASS " + kind + " share flow, persisted worker success, extracted text and document row");
        } finally {
            context.getContentResolver().delete(uri, null, null);
            input.delete();
        }
    }

    private File workDatabase() {
        File file = context.getDatabasePath("androidx.work.workdb");
        return file.isFile() ? file : new File(context.getNoBackupFilesDir(), "androidx.work.workdb");
    }
    private Set<String> importWorkers() {
        Set<String> ids = new HashSet<>();
        File file = workDatabase();
        if (file.isFile()) try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            Cursor cursor = db.rawQuery("SELECT id FROM workspec", null)) {
            while (cursor.moveToNext()) ids.add(cursor.getString(0));
        }
        return ids;
    }
    private long documentCount() {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath("veritas_reader.db").getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor row = db.rawQuery("SELECT COUNT(*) FROM documents", null)) {
            require(row.moveToFirst(), "Cannot count documents"); return row.getLong(0);
        }
    }
    private String data(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) return "{}";
        StringBuilder decoded = new StringBuilder();
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            int size = stream.readInt();
            require(size >= 0 && size <= 100, "Invalid worker data size");
            for (int i = 0; i < size; i++) decoded.append(stream.readUTF()).append('=').append(stream.readObject()).append(';');
        }
        return decoded.toString();
    }
    private void click(String label) throws Exception {
        await("UI action " + label, 20_000, () -> {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root == null) return false;
            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            collectNodes(root, nodes);
            for (AccessibilityNodeInfo node : nodes) {
                if (!TARGET.contentEquals(String.valueOf(node.getPackageName()))) continue;
                String text = String.valueOf(node.getText()) + "\n" + String.valueOf(node.getContentDescription());
                boolean matches = false;
                for (String part : text.split("[\n,]")) if (part.trim().equalsIgnoreCase(label)) matches = true;
                if (!matches || !node.isVisibleToUser() || !node.isEnabled()) continue;
                AccessibilityNodeInfo clickable = node;
                while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
                if (clickable != null && clickable.isEnabled() && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (bounds.isEmpty()) continue;
                long time = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
                MotionEvent up = MotionEvent.obtain(time, time + 30, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
                down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try {
                    boolean pressed = getUiAutomation().injectInputEvent(down, true);
                    boolean released = getUiAutomation().injectInputEvent(up, true);
                    if (pressed && released) return true;
                } finally { down.recycle(); up.recycle(); }
            }
            return false;
        });
        waitForIdleSync();
    }
    private void collectNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> nodes) {
        if (node == null) return;
        nodes.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collectNodes(node.getChild(i), nodes);
    }
    private String uiText() {
        try { return describe(getUiAutomation().getRootInActiveWindow(), 0); }
        catch (Throwable ignored) { return "UI unavailable"; }
    }
    private String describe(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 20) return "";
        StringBuilder text = new StringBuilder();
        if (TARGET.contentEquals(String.valueOf(node.getPackageName()))) {
            if (node.getText() != null) text.append(node.getText()).append(" | ");
            if (node.getContentDescription() != null) text.append(node.getContentDescription()).append(" | ");
        }
        for (int i = 0; i < node.getChildCount(); i++) text.append(describe(node.getChild(i), depth + 1));
        return text.toString();
    }
    private void await(String label, long timeout, Check check) throws Exception {
        long until = SystemClock.elapsedRealtime() + timeout;
        while (!check.ready()) {
            require(SystemClock.elapsedRealtime() < until, "Timed out: " + label + "; UI=" + uiText());
            SystemClock.sleep(200);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void report(String message) { Bundle status = new Bundle(); status.putString("stream", message + "\n"); sendStatus(0, status); }
}
