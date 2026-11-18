package com.example.passive_edge_ocr_telemetry;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "ConfigAsistenteDidi";
    public static final String KEY_TARIFA = "tarifa_minima";
    public static final String KEY_VALOR_KM = "valor_min_km";
    public static final String KEY_ARRENDAMIENTOS = "min_arrendamientos";

    private EditText etTarifaMinima, etValorKm, etMinArrendamientos;
    private TextToSpeech ttsPrueba;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etTarifaMinima = findViewById(R.id.etTarifaMinima);
        etValorKm = findViewById(R.id.etValorKm);
        etMinArrendamientos = findViewById(R.id.etMinArrendamientos);
        Button btnGuardar = findViewById(R.id.btnGuardar);
        Button btnProbarVoz = findViewById(R.id.btnProbarVoz);
        Button btnAccesibilidad = findViewById(R.id.btnAccesibilidad);

        // Cargar valores guardados previamente (o usar valores por defecto)
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etTarifaMinima.setText(String.valueOf(prefs.getInt(KEY_TARIFA, 20000)));
        etValorKm.setText(String.valueOf(prefs.getInt(KEY_VALOR_KM, 1500)));
        etMinArrendamientos.setText(String.valueOf(prefs.getInt(KEY_ARRENDAMIENTOS, 5)));

        // Inicializar TTS rápido para el botón de prueba
        ttsPrueba = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsPrueba.setLanguage(new Locale("es", "CO"));
                ttsPrueba.setSpeechRate(1.25f);
            }
        });

        // Guardar nuevos parámetros
        btnGuardar.setOnClickListener(v -> {
            try {
                int tarifa = Integer.parseInt(etTarifaMinima.getText().toString().trim());
                int valorKm = Integer.parseInt(etValorKm.getText().toString().trim());
                int arrendamientos = Integer.parseInt(etMinArrendamientos.getText().toString().trim());

                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(KEY_TARIFA, tarifa);
                editor.putInt(KEY_VALOR_KM, valorKm);
                editor.putInt(KEY_ARRENDAMIENTOS, arrendamientos);
                editor.apply();

                Toast.makeText(this, "Configuración guardada correctamente", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Por favor ingresa solo números válidos", Toast.LENGTH_SHORT).show();
            }
        });

        // Probar audio hacia el intercomunicador
        btnProbarVoz.setOnClickListener(v -> {
            if (ttsPrueba != null) {
                ttsPrueba.speak("Prueba de audio. Tarifa mínima configurada en " + etTarifaMinima.getText().toString() + " pesos.",
                        TextToSpeech.QUEUE_FLUSH, null, "PRUEBA");
            }
        });

        // Acceso directo a permisos de accesibilidad
        btnAccesibilidad.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        if (ttsPrueba != null) {
            ttsPrueba.stop();
            ttsPrueba.shutdown();
        }
        super.onDestroy();
    }
}
