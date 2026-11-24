/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import android.content.SharedPreferences;
import android.view.ViewGroup;

public class NoteEditor extends Activity {

    private static final String TAG = "NoteEditor";
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int EXPORT_TXT_REQUEST = 2;
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;
    private static final String ORIGINAL_CONTENT = "origContent";

    private static final String[] PROJECTION = new String[]{
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            NotePad.Notes.COLUMN_NAME_REMINDER_TIME,
            NotePad.Notes.COLUMN_NAME_IMAGE_PATH,
            NotePad.Notes.COLUMN_NAME_BG_COLOR
    };

    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private EditText mTitle;
    private String mOriginalContent;

    private int selectedBgColor = 0xFFFFFDE7;
    private long selectedReminderTime = 0;
    private SharedPreferences preferences;
    private static final float DEFAULT_FONT_SIZE = 16.0f;

    private final List<String> imageList = new ArrayList<>();

    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        public LinedEditText(android.content.Context context, AttributeSet attrs) {
            super(context, attrs);
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Rect rect = mRect;
            Paint paint = mPaint;

            for (int i = 0; i < getLineCount(); i++) {
                int baseline = getLineBounds(i, rect);
                canvas.drawLine(
                        rect.left,
                        baseline + 1,
                        rect.right,
                        baseline + 1,
                        paint
                );
            }

            super.onDraw(canvas);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_editor);

        preferences =
                getSharedPreferences("note_preferences", MODE_PRIVATE);

        mText = (EditText) findViewById(R.id.note);
        mTitle = (EditText) findViewById(R.id.title);

        if (mText == null) {
            finish();
            return;
        }

        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_EDIT.equals(action)) {
            mState = STATE_EDIT;
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);

            if (mUri == null) {
                finish();
                return;
            }

            setResult(RESULT_OK, new Intent().setAction(mUri.toString()));
        } else {
            finish();
            return;
        }

        mCursor = getContentResolver().query(
                mUri,
                PROJECTION,
                null,
                null,
                null
        );

        if (mCursor != null && mCursor.moveToFirst()) {
            int titleIndex = mCursor.getColumnIndex(
                    NotePad.Notes.COLUMN_NAME_TITLE
            );
            if (titleIndex >= 0 && mTitle != null) {
                String savedTitle = mCursor.getString(titleIndex);
                if (savedTitle != null) {
                    mTitle.setText(savedTitle);
                }
            }

            int noteIndex = mCursor.getColumnIndex(
                    NotePad.Notes.COLUMN_NAME_NOTE
            );
            if (noteIndex >= 0) {
                String savedText = mCursor.getString(noteIndex);
                String normalText = "";
                imageTextList.clear();

                if (savedText != null) {
                    if (savedText.startsWith("[MAIN]")) {
                        String[] lines = savedText.split("\n", -1);
                        StringBuilder normalTextBuilder = new StringBuilder();

                        for (String line : lines) {
                            if (line.startsWith("[MAIN]")) {
                                String encodedMain =
                                        line.substring("[MAIN]".length());

                                normalText =
                                        decodeSavedText(encodedMain);

                            } else if (line.startsWith("[IMAGE_TEXT:")) {
                                int end = line.indexOf("]");
                                if (end > 0) {
                                    try {
                                        int index = Integer.parseInt(
                                                line.substring(
                                                        "[IMAGE_TEXT:".length(),
                                                        end
                                                )
                                        );

                                        String encodedText =
                                                line.substring(end + 1);

                                        while (imageTextList.size() <= index) {
                                            imageTextList.add("");
                                        }

                                        imageTextList.set(
                                                index,
                                                decodeSavedText(encodedText)
                                        );
                                    } catch (Exception e) {
                                        Log.w(
                                                TAG,
                                                "无法解析图片文字",
                                                e
                                        );
                                    }
                                }

                            } else if (line.startsWith("[TODO:")) {
                                int end = line.indexOf("]");
                                if (end >= 0) {
                                    boolean checked =
                                            line.substring(
                                                    "[TODO:".length(),
                                                    end
                                            ).equals("1");

                                    String encodedTodo =
                                            line.substring(end + 1);

                                    addTodoItem(
                                            decodeSavedText(encodedTodo),
                                            checked
                                    );
                                }

                            } else if (line.startsWith("[NOTE]")) {
                                String encodedCard =
                                        line.substring("[NOTE]".length());

                                addNoteCard(
                                        decodeSavedText(encodedCard)
                                );

                            } else if (!line.isEmpty()) {
                                // 兼容旧格式中的普通内容
                                if (normalTextBuilder.length() > 0) {
                                    normalTextBuilder.append("\n");
                                }
                                normalTextBuilder.append(line);
                            }
                        }

                        if (normalText.isEmpty()
                                && normalTextBuilder.length() > 0) {
                            normalText = normalTextBuilder.toString();
                        }

                    } else {
                        // 兼容未使用新格式保存的旧笔记
                        String[] lines = savedText.split("\n", -1);
                        StringBuilder oldNormalText = new StringBuilder();

                        for (String line : lines) {
                            if (line.startsWith("[ ] ")
                                    || line.startsWith("[x] ")) {
                                boolean checked =
                                        line.startsWith("[x] ");

                                addTodoItem(
                                        line.substring(4),
                                        checked
                                );
                            } else {
                                if (oldNormalText.length() > 0) {
                                    oldNormalText.append("\n");
                                }
                                oldNormalText.append(line);
                            }
                        }

                        normalText = oldNormalText.toString();
                    }

                    mText.setTextKeepState(normalText);
                }
            }

            int bgIndex = mCursor.getColumnIndex(
                    NotePad.Notes.COLUMN_NAME_BG_COLOR
            );
            if (bgIndex >= 0) {
                selectedBgColor = mCursor.getInt(bgIndex);
            }

            int reminderIndex = mCursor.getColumnIndex(
                    NotePad.Notes.COLUMN_NAME_REMINDER_TIME
            );
            if (reminderIndex >= 0) {
                selectedReminderTime =
                        mCursor.getLong(reminderIndex);
            }

            int imageIndex = mCursor.getColumnIndex(
                    NotePad.Notes.COLUMN_NAME_IMAGE_PATH
            );

            if (imageIndex >= 0) {
                String savedImage =
                        mCursor.getString(imageIndex);

                if (savedImage != null
                        && !savedImage.isEmpty()) {
                    String[] paths = savedImage.split(",");

                    LinearLayout container =
                            (LinearLayout) findViewById(
                                    R.id.content_container
                            );

                    int imageTextIndex = 0;

                    if (container != null) {
                        for (String path : paths) {
                            if (path == null
                                    || path.isEmpty()) {
                                continue;
                            }

                            try {
                                Uri savedUri = Uri.parse(path);

                                ImageView imageView =
                                        new ImageView(this);

                                LinearLayout.LayoutParams imageParams =
                                        new LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT
                                        );

                                imageParams.setMargins(0, 16, 0, 16);

                                imageView.setLayoutParams(imageParams);
                                imageView.setScaleType(
                                        ImageView.ScaleType.FIT_CENTER
                                );
                                imageView.setImageURI(savedUri);
                                container.addView(imageView);

                                imageList.add(path);

                                EditText imageText =
                                        new EditText(this);

                                imageText.setLayoutParams(
                                        new LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT
                                        )
                                );

                                imageText.setHint("继续输入文字...");
                                imageText.setBackground(null);
                                imageText.setTag("dynamic_text");

                                if (imageTextIndex < imageTextList.size()) {
                                    imageText.setText(
                                            imageTextList.get(imageTextIndex)
                                    );
                                }

                                if (preferences.getBoolean(
                                        "night_mode",
                                        false
                                )) {
                                    imageText.setTextColor(0xFFEEEEEE);
                                    imageText.setHintTextColor(0xFFAAAAAA);
                                } else {
                                    imageText.setTextColor(0xFF333333);
                                }

                                container.addView(imageText);
                                imageTextIndex++;

                            } catch (Exception e) {
                                Log.w(
                                        TAG,
                                        "无法加载笔记图片：" + path,
                                        e
                                );
                            }
                        }
                    }
                }
            }
        }

        if (selectedBgColor == 0) {
            selectedBgColor = 0xFFFFFDE7;
        }

        View btnChangeBg =
                findViewById(R.id.btn_change_bg);

        if (btnChangeBg != null) {
            btnChangeBg.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showBackgroundSelector();
                        }
                    }
            );
        }

        Button btnReminder =
                (Button) findViewById(R.id.btn_set_reminder);

        if (btnReminder != null) {
            btnReminder.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showReminderPicker();
                        }
                    }
            );
        }

        View btnAddImage =
                findViewById(R.id.btn_add_image);

        if (btnAddImage != null) {
            btnAddImage.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent imageIntent = new Intent(
                                    Intent.ACTION_PICK,
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            );

                            startActivityForResult(
                                    imageIntent,
                                    PICK_IMAGE_REQUEST
                            );
                        }
                    }
            );
        }

        View btnTodo =
                findViewById(R.id.btn_todo);

        if (btnTodo != null) {
            btnTodo.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            addTodoItem();
                        }
                    }
            );
        }

        LinearLayout btnNote =
                (LinearLayout) findViewById(R.id.btn_note);

        if (btnNote != null) {
            btnNote.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            addNoteCard();
                        }
                    }
            );
        }

        View btnExport =
                findViewById(R.id.btn_export);

        if (btnExport != null) {
            btnExport.setOnClickListener(
                    v -> startExportTxt()
            );
        }

        if (savedInstanceState != null) {
            mOriginalContent =
                    savedInstanceState.getString(
                            ORIGINAL_CONTENT
                    );
        }

        View btnPreferences =
                findViewById(R.id.btn_preferences);

        if (btnPreferences != null) {
            btnPreferences.setOnClickListener(
                    v -> showPreferenceDialog()
            );
        }

        applySavedPreferences();
    }

    private void showBackgroundSelector() {
        final String[] colors = {
                "白色",
                "黄色",
                "粉色",
                "蓝色",
                "绿色"
        };

        final int[] colorValues = {
                0xFFFFFFFF,
                0xFFFFFDE7,
                0xFFFFCDD2,
                0xFFBBDEFB,
                0xFFC8E6C9
        };

        new AlertDialog.Builder(this)
                .setTitle("选择背景颜色")
                .setItems(colors, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        preferences.edit()
                                .putBoolean("night_mode", false)
                                .putInt("normal_background", colorValues[which])
                                .apply();

                        selectedBgColor = colorValues[which];
                        applyNormalMode();

                        if (mCursor != null) {
                            updateNote(
                                    mText.getText().toString(),
                                    null
                            );
                        }
                    }
                })
                .show();
    }

    private void applyNoteBackground(int color) {
        if (color == 0) {
            color = 0xFFFFFDE7;
            selectedBgColor = color;
        }

        if (mText != null) {
            mText.setBackgroundColor(color);
        }

        ScrollView scrollView =
                (ScrollView) findViewById(R.id.editor_scroll);
        if (scrollView != null) {
            scrollView.setBackgroundColor(color);
        }

        LinearLayout container =
                (LinearLayout) findViewById(R.id.content_container);
        if (container != null) {
            container.setBackgroundColor(color);
        }
    }

    private void showReminderPicker() {
        Calendar calendar = Calendar.getInstance(
                TimeZone.getTimeZone("Asia/Shanghai"),
                Locale.CHINA
        );

        new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(
                            android.widget.DatePicker view,
                            int year,
                            int month,
                            int dayOfMonth
                    ) {
                        new TimePickerDialog(
                                NoteEditor.this,
                                new TimePickerDialog.OnTimeSetListener() {
                                    @Override
                                    public void onTimeSet(
                                            android.widget.TimePicker view,
                                            int hourOfDay,
                                            int minute
                                    ) {
                                        Calendar selectedCalendar =
                                                Calendar.getInstance(
                                                        TimeZone.getTimeZone(
                                                                "Asia/Shanghai"
                                                        ),
                                                        Locale.CHINA
                                                );

                                        selectedCalendar.set(
                                                year,
                                                month,
                                                dayOfMonth,
                                                hourOfDay,
                                                minute,
                                                0
                                        );

                                        selectedReminderTime =
                                                selectedCalendar
                                                        .getTimeInMillis();

                                        Toast.makeText(
                                                NoteEditor.this,
                                                "提醒时间已设置！",
                                                Toast.LENGTH_SHORT
                                        ).show();

                                        if (mCursor != null) {
                                            updateNote(
                                                    mText.getText().toString(),
                                                    null
                                            );
                                        }
                                    }
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                true
                        ).show();
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        if (mState == STATE_EDIT) {
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(
                    Menu.CATEGORY_ALTERNATIVE,
                    0,
                    0,
                    new ComponentName(this, NoteEditor.class),
                    null,
                    intent,
                    0,
                    null
            );
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mCursor == null || mText == null) {
            return;
        }

        String text = mText.getText().toString();
        String title = mTitle == null
                ? ""
                : mTitle.getText().toString();

        if (isFinishing()
                && text.length() == 0
                && title.isEmpty()) {
            setResult(RESULT_CANCELED);
            deleteNote();
        } else if (mState == STATE_EDIT) {
            updateNote(text, null);
        } else if (mState == STATE_INSERT) {
            updateNote(text, text);
            mState = STATE_EDIT;
        }
    }

    private Uri copyImageToAppStorage(Uri sourceUri) {
        File imageDir = new File(getFilesDir(), "note_images");

        if (!imageDir.exists() && !imageDir.mkdirs()) {
            Log.e(TAG, "无法创建图片目录");
            return null;
        }

        File targetFile = null;
        InputStream input = null;
        OutputStream output = null;

        try {
            targetFile = File.createTempFile("note_image_", ".img", imageDir);

            input = getContentResolver().openInputStream(sourceUri);
            if (input == null) {
                Log.e(TAG, "无法读取选中的图片");
                return null;
            }

            output = new FileOutputStream(targetFile);

            byte[] buffer = new byte[8192];
            int length;

            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }

            output.flush();
            return Uri.fromFile(targetFile);

        } catch (Exception e) {
            Log.e(TAG, "复制图片失败", e);

            if (targetFile != null && targetFile.exists()) {
                // 复制失败时清理不完整文件
                //noinspection ResultOfMethodCallIgnored
                targetFile.delete();
            }

            return null;

        } finally {
            try {
                if (input != null) {
                    input.close();
                }
                if (output != null) {
                    output.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "关闭图片文件流失败", e);
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == EXPORT_TXT_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri outputUri = data.getData();

                try (OutputStream output =
                             getContentResolver().openOutputStream(outputUri)) {
                    if (output == null) {
                        Toast.makeText(this, "无法写入文件", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    output.write(
                            buildExportText().getBytes(
                                    java.nio.charset.Charset.forName("UTF-8")
                            )
                    );
                    output.flush();

                    Toast.makeText(this, "笔记已导出", Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    Log.e(TAG, "导出笔记失败", e);
                    Toast.makeText(this, "导出失败：" + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }

            return;
        }

        if (requestCode != PICK_IMAGE_REQUEST
                || resultCode != RESULT_OK
                || data == null) {
            return;
        }

        Uri selectedImage = data.getData();
        if (selectedImage == null) {
            Toast.makeText(this, "没有获取到图片", Toast.LENGTH_SHORT).show();
            return;
        }

        // 立刻复制进应用私有目录，避免相册临时授权失效
        Uri storedImageUri = copyImageToAppStorage(selectedImage);
        if (storedImageUri == null) {
            Toast.makeText(this, "图片保存失败", Toast.LENGTH_SHORT).show();
            return;
        }

        imageList.add(storedImageUri.toString());

        LinearLayout container =
                (LinearLayout) findViewById(R.id.content_container);

        if (container != null) {
            ImageView newImage = new ImageView(this);

            LinearLayout.LayoutParams imageParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
            imageParams.setMargins(0, 16, 0, 16);

            newImage.setLayoutParams(imageParams);
            newImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            newImage.setImageURI(storedImageUri);
            container.addView(newImage);

            EditText newText = new EditText(this);
            newText.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            );
            newText.setHint("继续输入文字...");
            newText.setBackground(null);
            newText.setTextSize(16);
            newText.setTag("dynamic_text");
            container.addView(newText);

            newText.requestFocus();
            newText.setTag("dynamic_text");

            if (preferences.getBoolean("night_mode", false)) {
                newText.setTextColor(0xFFEEEEEE);
                newText.setHintTextColor(0xFFAAAAAA);
            } else {
                newText.setTextColor(0xFF333333);
            }
        }

        // 立即把图片路径写入笔记
        if (mUri != null && mText != null) {
            updateNote(mText.getText().toString(), null);
        }

        Toast.makeText(this, "图片已添加", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mText != null) {
            outState.putString(
                    ORIGINAL_CONTENT,
                    mText.getText().toString()
            );
        }
    }

    @Override
    protected void onDestroy() {
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
            mCursor = null;
        }

        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mCursor == null || mText == null) {
            return super.onPrepareOptionsMenu(menu);
        }

        int noteIndex =
                mCursor.getColumnIndex(
                        NotePad.Notes.COLUMN_NAME_NOTE
                );

        if (noteIndex >= 0 && mCursor.moveToFirst()) {
            String savedNote = mCursor.getString(noteIndex);
            String currentNote = mText.getText().toString();

            MenuItem revertItem = menu.findItem(R.id.menu_revert);
            if (revertItem != null) {
                revertItem.setVisible(
                        savedNote == null
                                || !savedNote.equals(currentNote)
                );
            }
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_save) {
            updateNote(
                    mText.getText().toString(),
                    null
            );
            finish();
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
        } else if (id == R.id.menu_revert) {
            cancelNote();
        }

        return super.onOptionsItemSelected(item);
    }

    private final void updateNote(String text, String title) {
        if (mUri == null || mText == null) {
            return;
        }

        if (mTitle != null) {
            title = mTitle.getText().toString();
        }

        // 每项独立编码，避免文字中的换行符破坏保存格式
        StringBuilder fullText = new StringBuilder();
        fullText.append("[MAIN]")
                .append(encodeSavedText(mText.getText().toString()));

        LinearLayout container =
                (LinearLayout) findViewById(R.id.content_container);

        int imageTextIndex = 0;

        if (container != null) {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);

                // 图片下方的文字，按图片顺序编号保存
                if (child instanceof EditText
                        && "dynamic_text".equals(child.getTag())) {
                    String imageText =
                            ((EditText) child).getText().toString();

                    fullText.append("\n[IMAGE_TEXT:")
                            .append(imageTextIndex)
                            .append("]")
                            .append(encodeSavedText(imageText));

                    imageTextIndex++;
                }

                // 保存代办文字和勾选状态
                if (child instanceof LinearLayout
                        && "todo_row".equals(child.getTag())) {
                    View checkView = child.findViewWithTag("todo_check");
                    android.widget.CheckBox checkBox =
                            checkView instanceof android.widget.CheckBox
                                    ? (android.widget.CheckBox) checkView
                                    : null;

                    View todoView = child.findViewWithTag("todo_text");
                    EditText todoText =
                            todoView instanceof EditText
                                    ? (EditText) todoView
                                    : null;

                    if (todoText != null) {
                        String todo = todoText.getText().toString();

                        if (!todo.trim().isEmpty()) {
                            fullText.append("\n[TODO:")
                                    .append(checkBox != null
                                            && checkBox.isChecked() ? "1" : "0")
                                    .append("]")
                                    .append(encodeSavedText(todo));
                        }
                    }
                }
                if (child instanceof LinearLayout
                        && "note_card".equals(child.getTag())) {

                    EditText cardText =
                            (EditText) child.findViewWithTag("note_card_text");

                    if (cardText != null) {
                        String card =
                                cardText.getText().toString().trim();

                        if (!card.isEmpty()) {
                            fullText.append("\n[NOTE]")
                                    .append(encodeSavedText(card));
                        }
                    }
                }
            }
        }

        ContentValues values = new ContentValues();
        values.put(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                System.currentTimeMillis()
        );
        values.put(
                NotePad.Notes.COLUMN_NAME_NOTE,
                fullText.toString()
        );
        values.put(
                NotePad.Notes.COLUMN_NAME_TITLE,
                title
        );
        values.put(
                NotePad.Notes.COLUMN_NAME_BG_COLOR,
                selectedBgColor
        );
        values.put(
                NotePad.Notes.COLUMN_NAME_REMINDER_TIME,
                selectedReminderTime
        );

        StringBuilder imageBuilder = new StringBuilder();
        for (String image : imageList) {
            if (image != null && !image.isEmpty()) {
                imageBuilder.append(image).append(",");
            }
        }
        values.put(
                NotePad.Notes.COLUMN_NAME_IMAGE_PATH,
                imageBuilder.toString()
        );

        getContentResolver().update(mUri, values, null, null);

        if (selectedReminderTime > 0) {
            setReminder(title, mText.getText().toString());
        }

    }

    private void setReminder(String title, String content) {
        AlarmManager alarmManager =
                (AlarmManager) getSystemService(ALARM_SERVICE);

        if (alarmManager == null) {
            return;
        }

        Intent alarmIntent =
                new Intent(this, AlarmReceiver.class);
        alarmIntent.putExtra("note_title", title);
        alarmIntent.putExtra("note_content", content);
        alarmIntent.putExtra(
                "note_id",
                ContentUris.parseId(mUri)
        );

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        100,
                        alarmIntent,
                        flags
                );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    selectedReminderTime,
                    pendingIntent
            );
        } else {
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    selectedReminderTime,
                    pendingIntent
            );
        }
    }

    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                mCursor.close();
                mCursor = null;

                if (mOriginalContent != null) {
                    ContentValues values = new ContentValues();
                    values.put(
                            NotePad.Notes.COLUMN_NAME_NOTE,
                            mOriginalContent
                    );
                    getContentResolver().update(
                            mUri,
                            values,
                            null,
                            null
                    );
                }
            } else if (mState == STATE_INSERT) {
                deleteNote();
            }
        }

        setResult(RESULT_CANCELED);
        finish();
    }

    private final void deleteNote() {
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
            mCursor = null;
        }

        if (mUri != null) {
            getContentResolver().delete(
                    mUri,
                    null,
                    null
            );
        }

        if (mText != null) {
            mText.setText("");
        }
    }

    private void addTodoItem() {
        addTodoItem("", false);
    }

    private void addTodoItem(String text, boolean checked) {
        LinearLayout container =
                (LinearLayout) findViewById(R.id.content_container);

        if (container == null) {
            return;
        }

        LinearLayout todoRow = new LinearLayout(this);
        todoRow.setOrientation(LinearLayout.HORIZONTAL);
        todoRow.setTag("todo_row");

        android.widget.CheckBox checkBox =
                new android.widget.CheckBox(this);
        checkBox.setTag("todo_check");
        checkBox.setChecked(checked);

        EditText todoText = new EditText(this);
        todoText.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f
                )
        );
        todoText.setText(text);
        todoText.setHint("添加代办...");
        todoText.setBackground(null);
        todoText.setTag("todo_text");

        todoRow.addView(checkBox);
        todoRow.addView(todoText);
        container.addView(todoRow);

        if (text.isEmpty()) {
            todoText.requestFocus();
        }
        todoText.setTag("todo_text");

        if (preferences.getBoolean("night_mode", false)) {
            todoText.setTextColor(0xFFEEEEEE);
            todoText.setHintTextColor(0xFFAAAAAA);
        } else {
            todoText.setTextColor(0xFF333333);
        }

    }

    private java.util.List<String> imageTextList =
            new java.util.ArrayList<>();

    private String encodeSavedText(String value) {
        return android.util.Base64.encodeToString(
                value.getBytes(java.nio.charset.Charset.forName("UTF-8")),
                android.util.Base64.NO_WRAP
        );
    }

    private String decodeSavedText(String encodedText) {
        try {
            byte[] decoded =
                    android.util.Base64.decode(
                            encodedText,
                            android.util.Base64.NO_WRAP
                    );

            return new String(
                    decoded,
                    java.nio.charset.Charset.forName("UTF-8")
            );
        } catch (Exception e) {
            return "";
        }
    }

    private void startExportTxt() {
        String title = mTitle == null
                ? "笔记"
                : mTitle.getText().toString().trim();

        if (title.isEmpty()) {
            title = "笔记";
        }

        // 避免文件名中出现不适合的字符
        title = title.replaceAll("[\\\\/:*?\"<>|]", "_");

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, title + ".txt");

        startActivityForResult(intent, EXPORT_TXT_REQUEST);
    }

    private String buildExportText() {
        StringBuilder result = new StringBuilder();

        String title = mTitle == null
                ? ""
                : mTitle.getText().toString().trim();

        result.append(title.isEmpty() ? "无标题" : title)
                .append("\n")
                .append("================================\n\n");

        LinearLayout container =
                (LinearLayout)findViewById(R.id.content_container);

        if (container == null) {
            if (mText != null) {
                result.append(mText.getText().toString());
            }
            return result.toString();
        }

        // 按编辑器中控件的顺序导出，保持图片文字和待办的大致位置
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);

            if (child == mText) {
                appendExportText(result, mText.getText().toString());

            } else if (child instanceof ImageView) {
                result.append("\n[图片]\n");

            } else if (child instanceof EditText
                    && "dynamic_text".equals(child.getTag())) {
                appendExportText(
                        result,
                        ((EditText) child).getText().toString()
                );

            } else if (child instanceof LinearLayout
                    && "todo_row".equals(child.getTag())) {
                View checkView = child.findViewWithTag("todo_check");
                View textView = child.findViewWithTag("todo_text");

                String todo = textView instanceof EditText
                        ? ((EditText) textView).getText().toString().trim()
                        : "";

                if (!todo.isEmpty()) {
                    boolean checked =
                            checkView instanceof android.widget.CheckBox
                                    && ((android.widget.CheckBox) checkView)
                                    .isChecked();

                    result.append(checked ? "[x] " : "[ ] ")
                            .append(todo)
                            .append("\n");
                }
            }
            if (child instanceof LinearLayout
                    && "note_card".equals(child.getTag())) {

                EditText cardText =
                        (EditText) child.findViewWithTag("note_card_text");

                if (cardText != null
                        && !cardText.getText().toString().trim().isEmpty()) {
                    result.append("[便签] ")
                            .append(cardText.getText().toString().trim())
                            .append("\n\n");
                }
            }
        }

        return result.toString();
    }

    private void appendExportText(StringBuilder result, String text) {
        if (text != null && !text.trim().isEmpty()) {
            result.append(text.trim()).append("\n\n");
        }
    }
    private void applySavedPreferences() {
        float fontSize =
                preferences.getFloat("font_size", DEFAULT_FONT_SIZE);
        boolean nightMode =
                preferences.getBoolean("night_mode", false);

        if (mText != null) {
            mText.setTextSize(fontSize);

            // 同时设置图片下方和待办中的文字框
            LinearLayout container =
                    (LinearLayout) findViewById(R.id.content_container);

            if (container != null) {
                applyFontToTextControls(container, fontSize);
            }

            mText.setTextColor(
                    nightMode ? 0xFFEEEEEE : 0xFF333333
            );
        }

        if (nightMode) {
            applyNightMode();
        } else {
            applyNormalMode();
        }
    }

    private void applyFontToTextControls(
            ViewGroup container,
            float fontSize
    ) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);

            if (child instanceof EditText) {
                ((EditText) child).setTextSize(fontSize);
            }

            if (child instanceof ViewGroup) {
                applyFontToTextControls(
                        (ViewGroup) child,
                        fontSize
                );
            }
        }
    }

    private void showPreferenceDialog() {
        final String[] fontSizes = {
                "小号",
                "默认",
                "大号",
                "超大"
        };

        final float[] sizeValues = {
                14.0f,
                16.0f,
                18.0f,
                22.0f
        };

        boolean nightMode =
                preferences.getBoolean("night_mode", false);

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);

        builder.setTitle("偏好设置");

        builder.setSingleChoiceItems(
                fontSizes,
                getCurrentFontIndex(),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(
                            DialogInterface dialog,
                            int which
                    ) {
                        preferences.edit()
                                .putFloat("font_size", sizeValues[which])
                                .apply();

                        applySavedPreferences();
                        dialog.dismiss();
                    }
                }
        );

        builder.setNeutralButton(
                nightMode ? "切换为日间模式" : "切换为夜间模式",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(
                            DialogInterface dialog,
                            int which
                    ) {
                        boolean currentNight =
                                preferences.getBoolean("night_mode", false);

                        if (!currentNight) {
                            preferences.edit()
                                    .putInt("normal_background", selectedBgColor)
                                    .apply();
                        }

                        preferences.edit()
                                .putBoolean("night_mode", !currentNight)
                                .apply();

                        applySavedPreferences();
                    }
                }
        );

        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private int getCurrentFontIndex() {
        float size =
                preferences.getFloat("font_size", DEFAULT_FONT_SIZE);

        if (size <= 14.5f) return 0;
        if (size <= 16.5f) return 1;
        if (size <= 20.0f) return 2;
        return 3;
    }

    private void applyNightMode() {
        int nightBackground = 0xFF1E1E1E;

        ScrollView scrollView =
                (ScrollView) findViewById(R.id.editor_scroll);

        LinearLayout container =
                (LinearLayout) findViewById(R.id.content_container);

        if (scrollView != null) {
            scrollView.setBackgroundColor(nightBackground);
        }

        if (container != null) {
            container.setBackgroundColor(nightBackground);
            setTextViewsColor(container, 0xFFEEEEEE);
        }

        if (mText != null) {
            mText.setBackgroundColor(nightBackground);
            mText.setTextColor(0xFFEEEEEE);
            mText.setHintTextColor(0xFFAAAAAA);
        }

        if (mTitle != null) {
            mTitle.setTextColor(0xFFFFFFFF);
            mTitle.setHintTextColor(0xFFAAAAAA);
        }

        View root = findViewById(R.id.editor_root);
        if (root != null) {
            root.setBackgroundColor(nightBackground);
        }
    }

    private void applyNormalMode() {
        int savedColor = selectedBgColor;

        if (savedColor == 0) {
            savedColor = preferences.getInt(
                    "normal_background",
                    0xFFFFFDE7
            );
        }

        selectedBgColor = savedColor;

        ScrollView scrollView =
                (ScrollView) findViewById(R.id.editor_scroll);

        LinearLayout container =
                (LinearLayout) findViewById(R.id.content_container);

        if (scrollView != null) {
            scrollView.setBackgroundColor(savedColor);
        }

        if (container != null) {
            container.setBackgroundColor(savedColor);
            setTextViewsColor(container, 0xFF333333);
        }

        if (mText != null) {
            mText.setBackgroundColor(savedColor);
            mText.setTextColor(0xFF333333);
            mText.setHintTextColor(0xFF888888);
        }

        if (mTitle != null) {
            mTitle.setTextColor(0xFFFFFFFF);
            mTitle.setHintTextColor(0xFFAAAAAA);
        }

        View root = findViewById(R.id.editor_root);
        if (root != null) {
            root.setBackgroundColor(savedColor);
        }
    }

    private void setTextViewsColor(ViewGroup group, int color) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof EditText) {
                EditText editText = (EditText) child;
                editText.setTextColor(color);

                editText.setHintTextColor(
                        color == 0xFFEEEEEE
                                ? 0xFFAAAAAA
                                : 0xFF888888
                );

            } else if (child instanceof ViewGroup) {
                setTextViewsColor((ViewGroup) child, color);
            }
        }
    }
    private void addNoteCard() {
        LinearLayout container =
                (LinearLayout) findViewById(R.id.content_container);

        if (container == null) {
            return;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setTag("note_card");

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(16, 16, 16, 16);
        card.setLayoutParams(cardParams);
        card.setPadding(24, 24, 24, 24);
        card.setBackgroundColor(0xFFFFF59D);

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        EditText noteText = new EditText(this);
        noteText.setHint("输入便签内容...");
        noteText.setTag("note_card_text");
        noteText.setBackground(null);
        noteText.setTextSize(16);
        noteText.setTextColor(0xFF333333);

        card.addView(noteText);
        container.addView(card);

        noteText.requestFocus();

        boolean nightMode =
                preferences.getBoolean("night_mode", false);

        if (nightMode) {
            card.setBackgroundColor(0xFF4A4A4A);
            noteText.setTextColor(0xFFEEEEEE);
            noteText.setHintTextColor(0xFFAAAAAA);
        } else {
            card.setBackgroundColor(0xFFFFF59D);
            noteText.setTextColor(0xFF333333);
        }
    }
    private void addNoteCard(String text) {
        LinearLayout container =
                (LinearLayout) findViewById(R.id.content_container);

        if (container == null) {
            return;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setTag("note_card");
        card.setPadding(24, 24, 24, 24);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        cardParams.setMargins(16, 16, 16, 16);
        card.setLayoutParams(cardParams);

        EditText noteText = new EditText(this);
        noteText.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );
        noteText.setText(text);
        noteText.setHint("输入便签内容...");
        noteText.setTag("note_card_text");
        noteText.setBackground(null);
        noteText.setTextSize(
                preferences.getFloat("font_size", DEFAULT_FONT_SIZE)
        );

        if (preferences.getBoolean("night_mode", false)) {
            card.setBackgroundColor(0xFF4A4A4A);
            noteText.setTextColor(0xFFEEEEEE);
            noteText.setHintTextColor(0xFFAAAAAA);
        } else {
            card.setBackgroundColor(0xFFFFF59D);
            noteText.setTextColor(0xFF333333);
            noteText.setHintTextColor(0xFF888888);
        }

        card.addView(noteText);
        container.addView(card);
    }
}