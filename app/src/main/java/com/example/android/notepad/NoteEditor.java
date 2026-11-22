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

import static com.example.android.notepad.R.*;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Button;
import android.app.AlertDialog;
import android.widget.ImageView;
import android.widget.LinearLayout;


/**
 * This Activity handles "editing" a note, where editing is responding to
 * {@link Intent#ACTION_VIEW} (request to view data), edit a note
 * {@link Intent#ACTION_EDIT}, create a note {@link Intent#ACTION_INSERT}, or
 * create a new note from the current contents of the clipboard {@link Intent#ACTION_PASTE}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler}
 * or {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NoteEditor extends Activity {

    private int selectedBgColor = 0xFFFFFDE7;

    private long selectedReminderTime = 0;
    // For logging and debugging purposes
    private static final String TAG = "NoteEditor";

    private ImageView ivPreview;
    private String imageUriString = ""; // 存图片的路径
    private static final int PICK_IMAGE_REQUEST = 1;

    /*
     * Creates a projection that returns the note ID and the note contents.
     */
    private static final String[] PROJECTION =
        new String[] {
                NotePad.Notes._ID,
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_NOTE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_REMINDER_TIME,
                NotePad.Notes.COLUMN_NAME_IMAGE_PATH
    };

    // A label for the saved state of the activity
    private static final String ORIGINAL_CONTENT = "origContent";

    // This Activity can be started by more than one action. Each action is represented
    // as a "state" constant
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // Global mutable variables
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private EditText mTitle;
    private String mOriginalContent;
    private java.util.List<String> imageList = new java.util.ArrayList<>(); // 存所有图片路径
    private java.util.List<EditText> textList = new java.util.ArrayList<>(); // 存所有的文字框（可选）

    /**
     * Defines a custom EditText View that draws lines between each line of text that is displayed.
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        // This constructor is used by LayoutInflater
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // Creates a Rect and a Paint object, and sets the style and color of the Paint object.
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

        /**
         * This is called to draw the LinedEditText object
         * @param canvas The canvas on which the background is drawn.
         */
        @Override
        protected void onDraw(Canvas canvas) {

            // Gets the number of lines of text in the View.
            int count = getLineCount();

            // Gets the global Rect and Paint objects
            Rect r = mRect;
            Paint paint = mPaint;

            /*
             * Draws one line in the rectangle for every line of text in the EditText
             */
            for (int i = 0; i < count; i++) {

                // Gets the baseline coordinates for the current line of text
                int baseline = getLineBounds(i, r);

                /*
                 * Draws a line in the background from the left of the rectangle to the right,
                 * at a vertical position one dip below the baseline, using the "paint" object
                 * for details.
                 */
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // Finishes up by calling the parent method
            super.onDraw(canvas);
        }
    }

    /**
     * This method is called by Android when the Activity is first started. From the incoming
     * Intent, it determines what kind of editing is desired, and then does it.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_editor);

        if (imageList == null) {
            imageList = new java.util.ArrayList<>();
        }

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (Intent.ACTION_EDIT.equals(action)) {
            mState = STATE_EDIT;
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action) || Intent.ACTION_PASTE.equals(action)) {
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);
            if (mUri == null) {
                finish();
                return;
            }
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));
        } else {
            finish();
            return;
        }

        mText = (EditText) findViewById(R.id.note);
        mTitle = (EditText) findViewById(R.id.title);
        mText.setBackgroundColor(0xFFFFFDE7);

        mCursor = getContentResolver().query(mUri, PROJECTION, null, null, null);
        if (mCursor != null && mCursor.moveToFirst()) {
            try {
                String savedTitle = mCursor.getString(mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE));
                if (mTitle != null && savedTitle != null) mTitle.setText(savedTitle);

                String savedText = mCursor.getString(mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE));
                if (savedText != null) mText.setTextKeepState(savedText);

                // 读背景色
                int bgIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_BG_COLOR);
                if (bgIndex != -1) {
                    selectedBgColor = mCursor.getInt(bgIndex);
                    mText.setBackgroundColor(selectedBgColor != 0 ? selectedBgColor : 0xFFFFFDE7);
                }

                // 读图片
                int imgIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_IMAGE_PATH);
                if (imgIndex != -1) {
                    String savedImage = mCursor.getString(imgIndex);
                    if (savedImage != null && !savedImage.isEmpty()) {
                        String[] paths = savedImage.split(",");
                        LinearLayout container = (LinearLayout) findViewById(R.id.content_container);
                        if (container != null) {
                            for (String path : paths) {
                                if (!path.isEmpty()) {
                                    imageList.add(path);
                                    ImageView newImage = new ImageView(NoteEditor.this);
                                    LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT);
                                    imgParams.setMargins(0, 16, 0, 16);
                                    newImage.setLayoutParams(imgParams);
                                    newImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                    newImage.setImageURI(android.net.Uri.parse(path));
                                    container.addView(newImage);

                                    EditText newText = new EditText(NoteEditor.this);
                                    newText.setLayoutParams(new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT));
                                    newText.setHint("继续输入文字...");
                                    newText.setBackground(null);
                                    newText.setTag("dynamic_text"); // 👈 重点标记
                                    container.addView(newText);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        View btnChangeBg = findViewById(R.id.btn_change_bg);
        if (btnChangeBg != null) {
            btnChangeBg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String[] colors = {"白色", "黄色", "粉色", "蓝色", "绿色"};
                    final int[] colorValues = {0xFFFFFFFF, 0xFFFFFDE7, 0xFFFFCDD2, 0xFFBBDEFB, 0xFFC8E6C9};
                    new AlertDialog.Builder(NoteEditor.this)
                            .setTitle("选择背景颜色")
                            .setItems(colors, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mText.setBackgroundColor(colorValues[which]);
                                    findViewById(android.R.id.content).setBackgroundColor(colorValues[which]);

                                    selectedBgColor = colorValues[which];
                                }
                            }).show();
                }
            });
        }

        Button btnReminder = (Button) findViewById(R.id.btn_set_reminder);
        if (btnReminder != null) {
            btnReminder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    java.util.Calendar calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"), java.util.Locale.CHINA);
                    new android.app.DatePickerDialog(NoteEditor.this, (view, year, month, dayOfMonth) -> {
                        new android.app.TimePickerDialog(NoteEditor.this, (timeView, hourOfDay, minute) -> {
                            java.util.Calendar setCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"), java.util.Locale.CHINA);
                            setCal.set(year, month, dayOfMonth, hourOfDay, minute, 0);
                            selectedReminderTime = setCal.getTimeInMillis();
                            android.widget.Toast.makeText(NoteEditor.this, "提醒时间已设置！", android.widget.Toast.LENGTH_SHORT).show();
                        }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), true).show();
                    }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
                }
            });
        }

        View btnAddImage = findViewById(R.id.btn_add_image);
        if (btnAddImage != null) {
            btnAddImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent imgIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(imgIntent, PICK_IMAGE_REQUEST);
                }
            });
        }

        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 加载右上角的菜单布局（包含保存、删除、撤销按钮）
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        // 如果是编辑已有笔记，加上额外的菜单项
        if (mState == STATE_EDIT) {
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 这里留空，防止老代码把 UI 刷掉
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCursor != null) {
            String text = mText.getText().toString();
            // 只有在标题和正文都为空时，才认为用户是想删除笔记
            if (isFinishing() && text.length() == 0 && (mTitle == null || mTitle.getText().toString().isEmpty())) {
                setResult(RESULT_CANCELED);
                deleteNote();
            } else if (mState == STATE_EDIT) {
                updateNote(text, null);
            } else if (mState == STATE_INSERT) {
                updateNote(text, text);
                mState = STATE_EDIT;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            android.net.Uri selectedImage = data.getData();
            if (selectedImage != null) {
                // 1. 把图片路径加到列表里
                imageList.add(selectedImage.toString());
                // 2. 获取容器
                LinearLayout container = (LinearLayout) findViewById(R.id.content_container);
                // 3. 动态生成图片
                ImageView newImage = new ImageView(NoteEditor.this);
                LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                imgParams.setMargins(0, 16, 0, 16);
                newImage.setLayoutParams(imgParams);
                newImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                newImage.setImageURI(selectedImage);
                container.addView(newImage);

                // 4. 动态生成新的文字框，并给个特殊的 Tag 方便以后找
                EditText newText = new EditText(NoteEditor.this);
                newText.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                newText.setHint("继续输入文字...");
                newText.setBackground(null);
                newText.setTextSize(16);
                newText.setTag("dynamic_text");
                container.addView(newText);
                newText.requestFocus();
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mCursor == null) {
            return super.onPrepareOptionsMenu(menu);
        }
        try {
            int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            if (mCursor.moveToFirst()) {
                String savedNote = mCursor.getString(colNoteIndex);
                String currentNote = mText.getText().toString();
                if (savedNote != null && savedNote.equals(currentNote)) {
                    menu.findItem(R.id.menu_revert).setVisible(false);
                } else {
                    menu.findItem(R.id.menu_revert).setVisible(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            String text = mText.getText().toString();
            updateNote(text, null);
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
        if (mTitle != null) {
            title = mTitle.getText().toString();
        }
        StringBuilder fullText = new StringBuilder();

        // 1. 先拿主输入框的文字
        fullText.append(mText.getText().toString());

        // 2. 再去容器里找所有动态加的文字框，把里面的字也拼进去
        LinearLayout container = (LinearLayout) findViewById(R.id.content_container);
        if (container != null) {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                // 如果是个输入框，并且是我们加的那个，就把字拼上
                if (child instanceof EditText && "dynamic_text".equals(child.getTag())) {
                    String extraText = ((EditText) child).getText().toString();
                    if (!extraText.isEmpty()) {
                        fullText.append("\n").append(extraText); // 换行拼上去
                    }
                }
            }
        }

        // 3. 存入数据库
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, fullText.toString()); // 存拼接后的完整文本
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_BG_COLOR, selectedBgColor);

        // 4. 存图片路径
        StringBuilder sb = new StringBuilder();
        if (imageList != null) {
            for (String img : imageList) {
                sb.append(img).append(",");
            }
        }
        values.put(NotePad.Notes.COLUMN_NAME_IMAGE_PATH, sb.toString());
        if (selectedReminderTime > 0) {
            values.put(NotePad.Notes.COLUMN_NAME_REMINDER_TIME, selectedReminderTime);
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            android.content.Intent alarmIntent = new android.content.Intent(this, AlarmReceiver.class);
            alarmIntent.putExtra("note_title", title);
            alarmIntent.putExtra("note_content", fullText.toString());
            alarmIntent.putExtra("note_id", android.content.ContentUris.parseId(mUri));
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                    this, 100, alarmIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, selectedReminderTime, pendingIntent);
            } else {
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, selectedReminderTime, pendingIntent);
            }
        }
        getContentResolver().update(mUri, values, null, null);
    }

    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }
}
