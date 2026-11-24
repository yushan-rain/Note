# NotePad
项目来自于官方早期的数据库操作的基本教程。
更多的解析参考[Android Sample--NotePad解析](https://blog.csdn.net/llfjfz/article/details/67638499)
# 基于 Android 的记事本应用

本项目基于 Android 原生开发，使用 Java 实现。应用可以用于日常文字记录、代办事项管理、图片笔记保存、内容查询以及提醒管理。项目在原有 Android NotePad 示例基础上进行了功能扩展，并采用 SQLite 与 SharedPreferences 保存应用数据和偏好设置。

## 功能介绍

### 基本功能

- 界面采用笔记条目列表，显示笔记标题、正文摘要以及修改时间戳。
- 支持通过标题或正文关键字查询笔记，查询结果实时更新。
- 支持创建新笔记、编辑已有笔记和删除不需要的笔记。
- 支持分别编辑笔记标题和正文，并可保存笔记的最新修改内容。
- 界面退出、切换页面或保存笔记时自动更新数据库中的笔记内容。

### 附加功能

- 笔记修改后会更新修改时间，并在笔记列表中显示最新修改时间。
- 支持选择相册中的图片，并将图片添加到笔记内。
- 图片会复制到应用私有目录保存，避免相册临时访问权限失效。
- 支持为图片下方添加文字，图片和图片文字会随笔记一同保存。
- 支持在笔记中添加代办事项。
- 代办事项可编辑文本，并通过复选框修改完成状态。
- 支持创建便签卡，在便签卡中输入、保存和查看短文本内容。
- 支持为笔记选择背景颜色，包括：
  - 白色
  - 黄色
  - 粉色
  - 蓝色
  - 绿色
- 支持夜间模式和日间模式切换。
- 夜间模式下，笔记、标题、正文、代办和便签卡会切换到适合夜间阅读的颜色。
- 支持设置文字大小，包括“小号”“默认”“大号”和“超大”四种选项。
- 支持设置笔记提醒时间。
- 到达提醒时间后，应用会弹出提醒通知。
- 点击通知上的“关闭提醒”可取消该次提醒，关闭后不再重复提醒。
- 点击通知上的“查看笔记”可跳转到对应笔记并查看提醒内容。

- 支持将笔记标题、正文、代办事项、图片标记和便签卡内容导出为 TXT 文本文件。
- 笔记、标题、图片路径、提醒时间、背景颜色和修改时间均保存到本地数据库。
- 字体大小、夜间模式状态和日间背景偏好保存到 SharedPreferences。

## 系统和开发环境

- 开发语言：Java
- 开发平台：Android
- 开发工具：Android Studio
- 数据库：SQLite
- 数据访问方式：ContentProvider
- 偏好存储：SharedPreferences
- 构建工具：Gradle
- 最低或目标 Android API：请填写实际使用的 API 版本

## 项目运行方法

1. 使用 Android Studio 打开项目根目录。
2. 等待 Gradle 完成项目依赖同步。
3. 启动 Android 模拟器或连接已开启 USB 调试的 Android 真机。
4. 在设备中授予应用所需权限。
5. 点击 Android Studio 上方的 `Run` 按钮运行项目。

## 项目结构

```text
app/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/android/notepad/
│       │       ├── NotesList.java
│       │       ├── NoteEditor.java
│       │       ├── NotePad.java
│       │       ├── NotePadProvider.java
│       │       └── AlarmReceiver.java
│       ├── res/
│       │   ├── layout/
│       │   ├── menu/
│       │   ├── values/
│       │   └── drawable/
│       └── AndroidManifest.xml
└── build.gradle
```

## 核心使用说明与代码实现

### 一、笔记数据模型

笔记通过 `NotePad.Notes` 定义的字段保存，主要包括：

- `_ID`：笔记唯一编号
- `title`：笔记标题
- `note`：笔记正文及特殊控件内容
- `modification_date`：最后修改时间
- `reminder_time`：提醒时间
- `image_path`：图片路径
- `bg_color`：笔记背景颜色

核心查询字段定义在 `NoteEditor.java`：

```java
private static final String[] PROJECTION = new String[] {
        NotePad.Notes._ID,
        NotePad.Notes.COLUMN_NAME_TITLE,
        NotePad.Notes.COLUMN_NAME_NOTE,
        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
        NotePad.Notes.COLUMN_NAME_REMINDER_TIME,
        NotePad.Notes.COLUMN_NAME_IMAGE_PATH,
        NotePad.Notes.COLUMN_NAME_BG_COLOR
};
```

笔记修改时，通过 ContentResolver.update() 更新数据库：

```java
ContentValues values = new ContentValues();
values.put(
        NotePad.Notes.COLUMN_NAME_TITLE,
        mTitle.getText().toString()
);
values.put(
        NotePad.Notes.COLUMN_NAME_NOTE,
        fullText.toString()
);
values.put(
        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
        System.currentTimeMillis()
);
values.put(
        NotePad.Notes.COLUMN_NAME_BG_COLOR,
        selectedBgColor
);
values.put(
        NotePad.Notes.COLUMN_NAME_REMINDER_TIME,
        selectedReminderTime
);

getContentResolver().update(mUri, values, null, null);
```

### 二、笔记查询

在笔记列表中，通过输入的关键字查询标题或正文。

```java
String where = "("
        + NotePad.Notes.COLUMN_NAME_TITLE
        + " LIKE ? OR "
        + NotePad.Notes.COLUMN_NAME_NOTE
        + " LIKE ?)";

String[] whereArgs = new String[] {
        "%" + keyword + "%",
        "%" + keyword + "%"
};

Cursor cursor = getContentResolver().query(
        NotePad.Notes.CONTENT_URI,
        PROJECTION,
        where,
        whereArgs,
        NotePad.Notes.DEFAULT_SORT_ORDER
);
```

查询前应对关键字进行去除首尾空格处理：

```java
String keyword = searchEditText
        .getText()
        .toText()
        .toString()
        .trim();
```

当关键字为空时，可以直接查询所有笔记：

```java
getContentResolver().query(
        NotePad.Notes.CONTENT_URI,
        PROJECTION,
        null,
        null,
        NotePad.Notes.DEFAULT_SORT_ORDER
);
```

### 三、保存特殊笔记控件

普通正文、图片文字、代办事项和便签卡使用特殊标记保存到同一个笔记文本字段。

```text
[MAIN]编码后的普通正文
[IMAGE_TEXT:0]编码后的图片文字
[TODO:1]编码后的代办事项
[NOTE]编码后的便签卡内容
```

0 或 1 表示代办事项未完成或已完成。
```java
文字保存前使用 Base64 编码：
private String encodeSavedText(String value) {
    return android.util.Base64.encodeToString(
            value.getBytes(
                    java.nio.charset.Charset.forName("UTF-8")
            ),
            android.util.Base64.NO_WRAP
    );
}
```

读取时使用 Base64 解码：
```java
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
        Log.w(TAG, "文字解码失败", e);
        return "";
    }
}
```

### 四、新建代办事项

代办事项由复选框和输入框组成：
```java
private void addTodoItem() {
    addTodoItem("", false);
}
```

创建代办控件：
```java
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
}
```

保存代办事项：
```java
if (child instanceof LinearLayout
        && "todo_row".equals(child.getTag())) {

    View checkView =
            child.findViewWithTag("todo_check");

    View todoView =
            child.findViewWithTag("todo_text");

    android.widget.CheckBox checkBox =
            checkView instanceof android.widget.CheckBox
                    ? (android.widget.CheckBox) checkView
                    : null;

    EditText todoText =
            todoView instanceof EditText
                    ? (EditText) todoView
                    : null;

    if (todoText != null) {
        String todo =
                todoText.getText().toString();

        if (!todo.trim().isEmpty()) {
            fullText.append("\n[TODO:")
                    .append(checkBox != null
                            && checkBox.isChecked() ? "1" : "0")
                    .append("]")
                    .append(encodeSavedText(todo));
        }
    }
}
```

恢复代办事项：
```java
if (line.startsWith("[TODO:")) {
    int end = line.indexOf("]");

    if (end >= 0) {
        boolean checked =
                line.substring(
                        "[TODO:".length(),
                        end
                ).equals("1");

        String todo =
                line.substring(end + 1);

        addTodoItem(
                decodeSavedText(todo),
                checked
        );
    }
}
```

### 五、便签卡

####创建便签卡

便签卡由一个黄色外框和内部 EditText组成：
```java
private void addNoteCard() {
    LinearLayout container =
            (LinearLayout) findViewById(
                    R.id.content_container
            );

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
    noteText.setHint("输入便签内容...");
    noteText.setTag("note_card_text");
    noteText.setBackground(null);
    noteText.setTextSize(16);

    card.addView(noteText);
    container.addView(card);

    if (preferences.getBoolean("night_mode", false)) {
        card.setBackgroundColor(0xFF4A4A4A);
        noteText.setTextColor(0xFFEEEEEE);
    } else {
        card.setBackgroundColor(0xFFFFF59D);
        noteText.setTextColor(0xFF333333);
    }

    noteText.requestFocus();
}
```

按钮监听：
```XML
<LinearLayout
    android:id="@+id/btn_note"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:orientation="vertical"
    android:gravity="center"
    android:background="?android:attr/selectableItemBackground">

    <ImageView
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:src="@android:drawable/ic_menu_agenda"
        android:tint="#FFFFFF" />

    <TextView
        android:text="便签"
        android:textColor="#FFFFFF"
        android:textSize="12sp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
</LinearLayout>
```

Java监听：
```java
LinearLayout btnNote =
        (LinearLayout) findViewById(R.id.btn_note);

if (btnNote != null) {
    btnNote.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            addNoteCard();
        }
    });
}
```

保存便签卡
```java
if (child instanceof LinearLayout
        && "note_card".equals(child.getTag())) {

    EditText cardText =
            (EditText) child.findViewWithTag(
                    "note_card_text"
            );

    if (cardText != null) {
        String card =
                cardText.getText().toString().trim();

        if (!card.isEmpty()) {
            fullText.append("\n[NOTE]")
                    .append(encodeSavedText(card));
        }
    }
}
```

恢复便签卡
```java
if (line.startsWith("[NOTE]")) {
    String encodedCard =
            line.substring("[NOTE]".length());

    addNoteCard(
            decodeSavedText(encodedCard)
    );
}
```

### 六、图片保存

选择图片后使用系统相册的文件选择器：
```java
Intent imageIntent = new Intent(
        Intent.ACTION_PICK,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
);

startActivityForResult(
        imageIntent,
        PICK_IMAGE_REQUEST
);
```

为了避免相册临时授权失效，选择图片后复制到应用私有目录：
```java
private Uri copyImageToAppStorage(Uri sourceUri) {
    File imageDir =
            new File(getFilesDir(), "note_images");

    if (!imageDir.exists()) {
        imageDir.mkdirs();
    }

    try {
        File targetFile = File.createTempFile(
                "note_image_",
                ".img",
                imageDir
        );

        InputStream input =
                getContentResolver().openInputStream(sourceUri);

        OutputStream output =
                new FileOutputStream(targetFile);

        if (input == null) {
            return null;
        }

        byte[] buffer = new byte[8192];
        int length;

        while ((length = input.read(buffer)) != -1) {
            output.write(buffer, 0, length);
        }

        input.close();
        output.close();

        return Uri.fromFile(targetFile);

    } catch (Exception e) {
        Log.e(TAG, "复制图片失败", e);
        return null;
    }
}
```

保存到数据库时将图片路径拼接：
```java
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
```
恢复图片时应使用应用私有目录中的文件地址，并增加安全读取异常处理。

### 七、背景颜色和主题

可供选择的背景颜色：
```java
final int[] colorValues = {
        0xFFFFFFFF, // 白色
        0xFFFFFDE7, // 黄色
        0xFFFFCDD2, // 粉色
        0xFFBBDEFB, // 蓝色
        0xFFC8E6C9  // 绿色
};
```

保存用户选择的背景：
```java
preferences.edit()
        .putBoolean("night_mode", false)
        .putInt("normal_background", colorValues[which])
        .apply();

selectedBgColor = colorValues[which];
applyNormalMode();
```

主题设置使用 SharedPreferences保存：
```java
preferences.getBoolean("night_mode", false)
preferences.getFloat("font_size", DEFAULT_FONT_SIZE)
preferences.getInt("normal_background", 0xFFFFFDE7)
```

切换夜间模式：
```java
preferences.edit()
        .putBoolean(
                "night_mode",
                !preferences.getBoolean("night_mode", false)
        )
        .apply();

applySavedPreferences();
```

### 八、字体大小

字体大小选项：
```java
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
```

应用字体：
```java
float fontSize =
        preferences.getFloat(
                "font_size",
                DEFAULT_FONT_SIZE
        );

mText.setTextSize(fontSize);
applyFontToTextControls(container, fontSize);
```

### 九、提醒功能

选择提醒时间：
```java
selectedReminderTime =
        selectedCalendar.getTimeInMillis();
```

设置提醒：
```java
AlarmManager alarmManager =
        (AlarmManager) getSystemService(ALARM_SERVICE);

Intent alarmIntent =
        new Intent(this, AlarmReceiver.class);

alarmIntent.putExtra("note_title", title);
alarmIntent.putExtra("note_content", content);
alarmIntent.putExtra(
        "note_id",
        ContentUris.parseId(mUri)
);

PendingIntent pendingIntent =
        PendingIntent.getBroadcast(
                this,
                100,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        selectedReminderTime,
        pendingIntent
);
```
提醒通知中可提供：

查看笔记：根据通知中的笔记编号打开对应笔记
关闭提醒：取消当前提醒或不再触发提醒

### 十、TXT文件导出

使用系统文件选择器创建 TXT 文件：
```java
Intent intent =
        new Intent(Intent.ACTION_CREATE_DOCUMENT);

intent.addCategory(Intent.CATEGORY_OPENABLE);
intent.setType("text/plain");
intent.putExtra(
        Intent.EXTRA_TITLE,
        title + ".txt"
);

startActivityForResult(
        intent,
        EXPORT_TXT_REQUEST
);
```

选择文件后，将内容转换为 UTF-8 写入：
```java
try (OutputStream output =
             getContentResolver().openOutputStream(outputUri)) {

    output.write(
            buildExportText().getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
            )
    );

    output.flush();
}
```

导出格式
```text
笔记标题
================================

正文内容

代办事项
[ ] 未完成事项
[x] 已完成事项

[便签] 便签卡内容

[图片]
图片文字
```

### 十一、数据持久化与生命周期

笔记在以下时机保存：

- 点击保存
- 活动进入后台
- Activity退出
- 添加图片
- 修改背景颜色
- 设置提醒时间


