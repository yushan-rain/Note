package com.example.android.notepad;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        android.util.Log.d("ALARM_TEST", "闹钟到点了！准备弹窗！");

        String title = intent.getStringExtra("note_title");
        String content = intent.getStringExtra("note_content");
        long noteId = intent.getLongExtra("note_id", -1);

        // 启动弹窗 Activity
        Intent dialogIntent = new Intent(context, ReminderDialogActivity.class);
        dialogIntent.putExtra("note_title", title);
        dialogIntent.putExtra("note_content", content);
        dialogIntent.putExtra("note_id", noteId);

        // 必须加上这个 Flag，否则在后台无法启动 Activity
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(dialogIntent);
    }
}