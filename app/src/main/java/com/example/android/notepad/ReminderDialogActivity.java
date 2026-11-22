package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class ReminderDialogActivity extends Activity {
    private long noteId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 让这个 Activity 在锁屏上也能显示，并且点亮屏幕
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String title = getIntent().getStringExtra("note_title");
        String content = getIntent().getStringExtra("note_content");
        noteId = getIntent().getLongExtra("note_id", -1);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⏰ 笔记提醒: " + (title != null ? title : "无标题"))
                .setMessage(content != null ? content : "点击查看详情")
                .setCancelable(false)
                .setPositiveButton("跳转查看", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearReminderTime(); // 清除提醒时间

                        if (noteId != -1) {
                            // 跳转到具体的笔记详情
                            Uri noteUri = android.content.ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, noteId);
                            Intent intent = new Intent(ReminderDialogActivity.this, NoteEditor.class);
                            intent.setAction(Intent.ACTION_EDIT);
                            intent.setData(noteUri);
                            startActivity(intent);
                        } else {
                            // 没拿到 ID 就回主界面
                            startActivity(new Intent(ReminderDialogActivity.this, NotesList.class));
                        }
                        finish();
                    }
                })
                .setNegativeButton("关闭", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearReminderTime(); // 清除提醒时间
                        finish();
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void clearReminderTime() {
        if (noteId != -1) {
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_REMINDER_TIME, 0); // 0 代表没有提醒
            Uri noteUri = android.content.ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, noteId);
            getContentResolver().update(noteUri, values, null, null);
        }
    }
}