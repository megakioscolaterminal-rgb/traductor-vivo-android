package com.traductorvivo.app;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.microsoft.cognitiveservices.speech.AutoDetectSourceLanguageConfig;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.translation.SpeechTranslationConfig;
import com.microsoft.cognitiveservices.speech.translation.TranslationRecognizer;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int MIC_REQUEST = 41;
    private static final String PREFS = "traductor_vivo_config";
    private static final String VOICE = "es-ES-ElviraNeural";

    private EditText keyInput, regionInput, endpointInput;
    private TextView status, listeningLabel;
    private Button startButton, stopButton;
    private TranslationRecognizer recognizer;
    private AutoDetectSourceLanguageConfig autoDetect;
    private AudioConfig microphone;
    private SpeechTranslationConfig translationConfig;
    private SpeechSynthesizer synthesizer;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(8, 20, 38));
        getWindow().setNavigationBarColor(Color.rgb(8, 20, 38));
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        buildScreen();
        loadSettings();
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void buildScreen() {
        int navy = Color.rgb(8, 20, 38), white = Color.WHITE, muted = Color.rgb(174, 190, 211), cyan = Color.rgb(56, 189, 248);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(navy);
        root.setPadding(dp(24), dp(24), dp(24), dp(20));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        TextView logo = text("◉", 48, cyan, true); logo.setGravity(Gravity.CENTER); content.addView(logo);
        TextView title = text("TRADUCTOR VIVO", 25, white, true); title.setGravity(Gravity.CENTER); content.addView(title);
        TextView subtitle = text("TRADUCCIÓN DE VOZ EN TIEMPO REAL", 11, muted, true); subtitle.setGravity(Gravity.CENTER); subtitle.setPadding(0, dp(6), 0, dp(26)); content.addView(subtitle);

        TextView output = text("ESPAÑOL", 30, cyan, true); output.setGravity(Gravity.CENTER); content.addView(output);
        TextView outputSub = text("IDIOMA DE SALIDA · VOZ FEMENINA", 11, muted, true); outputSub.setGravity(Gravity.CENTER); outputSub.setPadding(0, 0, 0, dp(22)); content.addView(outputSub);

        TextView auto = text("DETECCIÓN AUTOMÁTICA", 14, white, true); auto.setGravity(Gravity.CENTER); content.addView(auto);
        TextView langs = text("Español  ·  English  ·  Português\nFrançais  ·  Deutsch  ·  Italiano", 14, muted, false);
        langs.setGravity(Gravity.CENTER); langs.setLineSpacing(dp(5), 1f); langs.setPadding(0, dp(10), 0, dp(28)); content.addView(langs);

        listeningLabel = text("●  LISTO PARA ESCUCHAR", 13, cyan, true); listeningLabel.setGravity(Gravity.CENTER); listeningLabel.setPadding(0, dp(12), 0, dp(8)); content.addView(listeningLabel);
        status = text("Configurá tu conexión para comenzar.", 13, muted, false); status.setGravity(Gravity.CENTER); status.setPadding(0, 0, 0, dp(18)); content.addView(status);

        startButton = new Button(this); startButton.setText("INICIAR CONVERSACIÓN"); startButton.setTextSize(15); startButton.setAllCaps(false);
        startButton.setOnClickListener(v -> requestAndStart());
        content.addView(startButton, new LinearLayout.LayoutParams(-1, dp(56)));
        stopButton = new Button(this); stopButton.setText("DETENER"); stopButton.setTextSize(14); stopButton.setAllCaps(false); stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> stopTranslation());
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(-1, dp(50)); stopLp.topMargin = dp(8); content.addView(stopButton, stopLp);

        TextView configTitle = text("CONFIGURACIÓN DE CONEXIÓN", 13, white, true); configTitle.setPadding(0, dp(28), 0, dp(8)); content.addView(configTitle);
        TextView note = text("Necesitás una clave de Azure AI Speech y la región de tu recurso. Se guardan únicamente en este teléfono.", 12, muted, false); content.addView(note);
        keyInput = new EditText(this); keyInput.setSingleLine(true); keyInput.setHint("Clave de Azure Speech"); keyInput.setTextColor(white); keyInput.setHintTextColor(muted); keyInput.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ); styleField(keyInput); content.addView(keyInput, new LinearLayout.LayoutParams(-1, dp(52)));
        regionInput = new EditText(this); regionInput.setSingleLine(true); regionInput.setHint("Región (ej.: eastus)"); regionInput.setTextColor(white); regionInput.setHintTextColor(muted); regionInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT); styleField(regionInput);
        LinearLayout.LayoutParams regionLp = new LinearLayout.LayoutParams(-1, dp(52)); regionLp.topMargin = dp(8); content.addView(regionInput, regionLp);
        endpointInput = new EditText(this); endpointInput.setSingleLine(true); endpointInput.setHint("Endpoint universal v2 de Azure (wss://...)"); endpointInput.setTextColor(white); endpointInput.setHintTextColor(muted); endpointInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); styleField(endpointInput);
        LinearLayout.LayoutParams endpointLp = new LinearLayout.LayoutParams(-1, dp(52)); endpointLp.topMargin = dp(8); content.addView(endpointInput, endpointLp);
        Button save = new Button(this); save.setText("GUARDAR CONFIGURACIÓN"); save.setAllCaps(false); save.setOnClickListener(v -> saveSettings());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(48)); saveLp.topMargin = dp(8); content.addView(save, saveLp);

        TextView footer = text("Solo audio · Requiere Internet · No muestra transcripciones", 10, muted, false); footer.setGravity(Gravity.CENTER); footer.setPadding(0, dp(22), 0, dp(8)); content.addView(footer);
        setContentView(root);
    }

    private void styleField(EditText field) {
        field.setPadding(dp(14), 0, dp(14), 0);
        field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(56, 189, 248)));
    }

    private void loadSettings() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        keyInput.setText(p.getString("key", "")); regionInput.setText(p.getString("region", "")); endpointInput.setText(p.getString("endpoint", ""));
    }

    private void saveSettings() {
        String key = keyInput.getText().toString().trim();
        String region = regionInput.getText().toString().trim();
        String endpoint = endpointInput.getText().toString().trim();
        if (key.isEmpty() || region.isEmpty() || endpoint.isEmpty()) { setStatus("Ingresá clave, región y endpoint universal v2 de Azure.", false); return; }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("key", key).putString("region", region).putString("endpoint", endpoint).apply();
        setStatus("Configuración guardada en este teléfono.", true);
    }

    private void requestAndStart() {
        if (keyInput.getText().toString().trim().isEmpty() || regionInput.getText().toString().trim().isEmpty() || endpointInput.getText().toString().trim().isEmpty()) {
            setStatus("Primero ingresá la clave, región y endpoint universal v2 de Azure.", false); return;
        }
        saveSettings();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST);
        } else startTranslation();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startTranslation();
        else setStatus("Necesitamos permiso de micrófono para escuchar.", false);
    }

    private void startTranslation() {
        if (running) return;
        String key = keyInput.getText().toString().trim();
        String region = regionInput.getText().toString().trim();
        String endpoint = endpointInput.getText().toString().trim();
        setStatus("Conectando con el servicio de traducción…", true);
        startButton.setEnabled(false);
        worker.execute(() -> {
            try {
                translationConfig = SpeechTranslationConfig.fromEndpoint(java.net.URI.create(endpoint), key);
                // Placeholder requerido por el SDK; la detección abierta determina el idioma real.
                translationConfig.setSpeechRecognitionLanguage("en-US");
                translationConfig.addTargetLanguage("es");
                translationConfig.setVoiceName(VOICE);
                autoDetect = AutoDetectSourceLanguageConfig.fromOpenRange();
                microphone = AudioConfig.fromDefaultMicrophoneInput();
                recognizer = new TranslationRecognizer(translationConfig, autoDetect, microphone);
                SpeechConfig voiceConfig = SpeechConfig.fromSubscription(key, region);
                voiceConfig.setSpeechSynthesisVoiceName(VOICE);
                synthesizer = new SpeechSynthesizer(voiceConfig, AudioConfig.fromDefaultSpeakerOutput());

                recognizer.recognized.addEventListener((s, e) -> {
                    if (e.getResult() != null && e.getResult().getReason() == ResultReason.TranslatedSpeech) {
                        Map<String, String> translations = e.getResult().getTranslations();
                        String spanish = translations.get("es");
                        if (spanish != null && !spanish.trim().isEmpty()) {
                            worker.execute(() -> speakSpanish(spanish));
                        }
                    }
                });
                recognizer.canceled.addEventListener((s, e) -> {
                    String details = e.getErrorDetails();
                    runOnUiThread(() -> setStatus("Error del servicio. Revisá la clave, región e Internet." + (details == null ? "" : ""), false));
                });
                recognizer.startContinuousRecognitionAsync().get();
                running = true;
                runOnUiThread(() -> {
                    startButton.setEnabled(false); stopButton.setEnabled(true);
                    listeningLabel.setText("●  ESCUCHANDO");
                    setStatus("Hablá en cualquiera de los idiomas admitidos. La traducción se escucha en español.", true);
                });
            } catch (Exception ex) {
                running = false;
                runOnUiThread(() -> { startButton.setEnabled(true); stopButton.setEnabled(false); setStatus("No se pudo iniciar. Revisá la clave, región e Internet.", false); });
                releaseSpeechObjects();
            }
        });
    }

    private void speakSpanish(String phrase) {
        try {
            if (synthesizer != null && running) synthesizer.SpeakTextAsync(phrase).get();
        } catch (Exception ignored) { }
    }

    private void stopTranslation() {
        if (!running) return;
        setStatus("Deteniendo…", true);
        worker.execute(() -> {
            try { if (recognizer != null) recognizer.stopContinuousRecognitionAsync().get(); } catch (Exception ignored) { }
            running = false;
            releaseSpeechObjects();
            runOnUiThread(() -> {
                startButton.setEnabled(true); stopButton.setEnabled(false);
                listeningLabel.setText("●  LISTO PARA ESCUCHAR"); setStatus("Conversación detenida.", true);
            });
        });
    }

    private void releaseSpeechObjects() {
        try { if (recognizer != null) recognizer.close(); } catch (Exception ignored) { }
        try { if (synthesizer != null) synthesizer.close(); } catch (Exception ignored) { }
        try { if (microphone != null) microphone.close(); } catch (Exception ignored) { }
        try { if (autoDetect != null) autoDetect.close(); } catch (Exception ignored) { }
        try { if (translationConfig != null) translationConfig.close(); } catch (Exception ignored) { }
        recognizer = null; synthesizer = null; microphone = null; autoDetect = null; translationConfig = null;
    }

    private void setStatus(String message, boolean good) {
        status.setText(message); status.setTextColor(good ? Color.rgb(134, 239, 172) : Color.rgb(252, 165, 165));
    }

    @Override protected void onDestroy() {
        if (running) {
            try { if (recognizer != null) recognizer.stopContinuousRecognitionAsync(); } catch (Exception ignored) { }
        }
        running = false;
        worker.shutdown();
        releaseSpeechObjects();
        super.onDestroy();
    }
}
