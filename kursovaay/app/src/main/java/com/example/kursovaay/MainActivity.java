package com.example.kursovaay;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final String API_KEY = "AIzaSyAz0NUhJITTy70PmkpIJcwUKwLS1ARKW34";
    private static final String TRANSLATE_API_URL = "https://translation.googleapis.com/language/translate/v2?key=" + API_KEY;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private EditText inputText;
    private TextView translatedText;
    private TextView translatedTextHint;
    private TextView translatingTextHint;
    private Button translateButton;
    private Spinner sourceLangSpinner, targetLangSpinner;
    private ImageButton switchLangButton;
    private ImageButton copyButton;
    private OkHttpClient client;
    private Animation translatingAnimation;

    private List<String> languageNames = new ArrayList<>();
    private Map<String, String> nameToCode = new HashMap<>();
    private final Map<String, String> hintTranslations = new HashMap<String, String>() {{
        put("en", "Translated text");
        put("ru", "Переведённый текст");
        put("uk", "Перекладений текст");
        put("de", "Übersetzter Text");
        put("fr", "Texte traduit");
        put("es", "Texto traducido");
    }};
    private final Map<String, String> translatingTranslations = new HashMap<String, String>() {{
        put("en", "Translating text...");
        put("ru", "Текст переводится...");
        put("uk", "Текст перекладається...");
        put("de", "Text wird übersetzt...");
        put("fr", "Traduction du texte...");
        put("es", "El texto se está traduciendo...");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputText = findViewById(R.id.inputText);
        translatedText = findViewById(R.id.translatedText);
        translatedTextHint = findViewById(R.id.translatedTextHint);
        translatingTextHint = findViewById(R.id.translatingTextHint);
        translateButton = findViewById(R.id.translateButton);
        sourceLangSpinner = findViewById(R.id.sourceLangSpinner);
        targetLangSpinner = findViewById(R.id.targetLangSpinner);
        switchLangButton = findViewById(R.id.switchLangButton);
        copyButton = findViewById(R.id.copyButton);
        client = new OkHttpClient();

        loadLanguages();

        translateButton.setOnClickListener(v -> translateText());
        switchLangButton.setOnClickListener(v -> switchLanguages());

        targetLangSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                updateTranslatedHint();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        updateTranslatedHint();

        copyButton.setOnClickListener(v -> {
            copyTranslatedText();
        });

        // Ограничение ввода текста
        inputText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Не используется
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Не используется
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 500) {
                    Toast.makeText(MainActivity.this, "Максимальное количество символов - 500", Toast.LENGTH_SHORT).show();
                    // Обрезать текст до 500 символов
                    s.delete(500, s.length());
                }
            }
        });

        // Анимация переливания
        translatingAnimation = new AlphaAnimation(0.3f, 1.0f);
        translatingAnimation.setDuration(700);
        translatingAnimation.setRepeatMode(Animation.REVERSE);
        translatingAnimation.setRepeatCount(Animation.INFINITE);
    }

    private void loadLanguages() {
        try {
            AssetManager assetManager = getAssets();
            InputStream is = assetManager.open("languages.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            Type listType = new TypeToken<List<Language>>(){}.getType();
            List<Language> languages = new Gson().fromJson(reader, listType);
            for (Language lang : languages) {
                languageNames.add(lang.name);
                nameToCode.put(lang.name, lang.code);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languageNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sourceLangSpinner.setAdapter(adapter);
            targetLangSpinner.setAdapter(adapter);
            // По умолчанию: исходный - русский, целевой - английский
            int ruIndex = languageNames.indexOf("Русский");
            int enIndex = languageNames.indexOf("Английский");
            if (ruIndex >= 0) sourceLangSpinner.setSelection(ruIndex);
            if (enIndex >= 0) targetLangSpinner.setSelection(enIndex);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка загрузки языков: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void translateText() {
        String text = inputText.getText().toString();
        String sourceLang = nameToCode.get(sourceLangSpinner.getSelectedItem().toString());
        String targetLang = nameToCode.get(targetLangSpinner.getSelectedItem().toString());
        if (text.isEmpty()) {
            Toast.makeText(this, "Введите текст для перевода", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sourceLang.equals(targetLang)) {
            translatedText.setText(text);
            updateTranslatedHint();
            return;
        }
        showTranslatingHint();
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("q", text);
        requestBody.addProperty("source", sourceLang);
        requestBody.addProperty("target", targetLang);
        requestBody.addProperty("format", "text");

        RequestBody body = RequestBody.create(requestBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(TRANSLATE_API_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    hideTranslatingHint();
                    Toast.makeText(MainActivity.this, 
                        "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    translatedText.setText("Ошибка: " + e.getMessage());
                    updateTranslatedHint();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    hideTranslatingHint();
                    if (!response.isSuccessful()) {
                        String error = "Ошибка сервера: " + response.code() + "\n" + responseBody;
                        Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        translatedText.setText(error);
                        updateTranslatedHint();
                        return;
                    }

                    try {
                        JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                        String translatedTextStr = jsonResponse.getAsJsonObject("data")
                                .getAsJsonArray("translations")
                                .get(0)
                                .getAsJsonObject()
                                .get("translatedText")
                                .getAsString();
                        MainActivity.this.translatedText.setText(translatedTextStr);
                        updateTranslatedHint();
                    } catch (Exception e) {
                        String error = "Ошибка парсинга: " + e.getMessage() + "\n" + responseBody;
                        Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        translatedText.setText(error);
                        updateTranslatedHint();
                    }
                });
            }
        });
    }

    private void switchLanguages() {
        int sourcePos = sourceLangSpinner.getSelectedItemPosition();
        int targetPos = targetLangSpinner.getSelectedItemPosition();
        sourceLangSpinner.setSelection(targetPos);
        targetLangSpinner.setSelection(sourcePos);
    }

    private void updateTranslatedHint() {
        String targetLang = nameToCode.get(targetLangSpinner.getSelectedItem().toString());
        String hint = hintTranslations.getOrDefault(targetLang, "Переведённый текст");
        translatedTextHint.setText(hint);
        if (translatedText.getText().toString().isEmpty()) {
            translatedTextHint.setVisibility(View.VISIBLE);
        } else {
            translatedTextHint.setVisibility(View.GONE);
        }
        updateCopyButtonVisibility();
    }

    private void showTranslatingHint() {
        String targetLang = nameToCode.get(targetLangSpinner.getSelectedItem().toString());
        String hint = translatingTranslations.getOrDefault(targetLang, "Текст переводится...");
        translatingTextHint.setText(hint);
        translatingTextHint.setVisibility(View.VISIBLE);
        translatingTextHint.startAnimation(translatingAnimation);
        translatedText.setText("");
        translatedTextHint.setVisibility(View.GONE);
    }

    private void hideTranslatingHint() {
        translatingTextHint.clearAnimation();
        translatingTextHint.setVisibility(View.GONE);
    }

    private void updateCopyButtonVisibility() {
        if (translatedText.getText().toString().isEmpty()) {
            copyButton.setVisibility(View.GONE);
        } else {
            copyButton.setVisibility(View.VISIBLE);
        }
    }

    private void copyTranslatedText() {
        String textToCopy = translatedText.getText().toString();
        if (textToCopy.isEmpty()) return;

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Переведенный текст", textToCopy);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, "Текст скопирован в буфер обмена", Toast.LENGTH_SHORT).show();
    }

    // Вспомогательный класс для языка
    private static class Language {
        String name;
        String code;
    }
}