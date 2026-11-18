package com.example.passive_edge_ocr_telemetry;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressLint("AccessibilityPolicy")
public class AsistentePasivoService extends AccessibilityService {

    private static final String TAG = "AsistenteDiDi";
    private TextToSpeech tts;
    private TextRecognizer textRecognizer;

    private Handler handlerVigilancia;
    private Runnable tareaVigilancia;
    private boolean didiEnPrimerPlano = false;
    private boolean procesandoOCR = false;

    private String ultimaOfertaAnunciada = "";
    private long ultimoTiempoAnuncio = 0;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Servicio conectado con Vigilancia Activa");

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.forLanguageTag("es-CO"));
                tts.setSpeechRate(1.25f);

                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                tts.setAudioAttributes(audioAttributes);

                // Apagar la pantalla SOLO cuando la voz ya empezó a sonar en el casco
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        if ("ID_OFERTA".equals(utteranceId)) {
                            // Pequeño margen de 200 ms para asegurar flujo estable antes de bloquear
                            handlerVigilancia.postDelayed(() -> apagarPantalla(), 200);
                        }
                    }

                    @Override
                    public void onDone(String utteranceId) {}

                    @Override
                    public void onError(String utteranceId) {}
                });
            }
        });


        // Iniciar reloj de vigilancia cada 600 ms
        handlerVigilancia = new Handler(Looper.getMainLooper());
        tareaVigilancia = new Runnable() {
            @Override
            public void run() {
                if (didiEnPrimerPlano && !procesandoOCR) {
                    ejecutarCapturaRapida();
                }
                handlerVigilancia.postDelayed(this, 600);
            }
        };
        handlerVigilancia.post(tareaVigilancia);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;

        int tipo = event.getEventType();
        if (tipo == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                tipo == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            String paquete = event.getPackageName().toString().toLowerCase();
            if (!paquete.equals("com.android.systemui")) {
                boolean estadoAnterior = didiEnPrimerPlano;
                didiEnPrimerPlano = paquete.contains("didi");

                if (estadoAnterior != didiEnPrimerPlano) {
                    Log.d(TAG, "Estado DiDi en pantalla: " + didiEnPrimerPlano + " (" + paquete + ")");
                }
            }
        }
    }

    private void ejecutarCapturaRapida() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            procesandoOCR = true;

            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(@NonNull ScreenshotResult screenshot) {
                    try {
                        Bitmap bitmapHardware = Bitmap.wrapHardwareBuffer(
                                screenshot.getHardwareBuffer(),
                                screenshot.getColorSpace()
                        );

                        if (bitmapHardware != null) {
                            Bitmap bitmapSoftware = bitmapHardware.copy(Bitmap.Config.ARGB_8888, false);
                            screenshot.getHardwareBuffer().close();
                            bitmapHardware.recycle();

                            int ancho = bitmapSoftware.getWidth();
                            int alto = bitmapSoftware.getHeight();
                            int inicioY = (int) (alto * 0.35);
                            int altoRecorte = alto - inicioY;

                            Bitmap recortado = Bitmap.createBitmap(bitmapSoftware, 0, inicioY, ancho, altoRecorte);
                            bitmapSoftware.recycle();

                            Bitmap escalado = Bitmap.createScaledBitmap(recortado, ancho / 2, altoRecorte / 2, false);
                            recortado.recycle();

                            procesarImagenConOCR(escalado);
                        } else {
                            procesandoOCR = false;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error captura: " + e.getMessage());
                        procesandoOCR = false;
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "Fallo captura código: " + errorCode);
                    procesandoOCR = false;
                }
            });
        }
    }

    private void procesarImagenConOCR(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        textRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    List<String> lineasDetectadas = new ArrayList<>();
                    for (Text.TextBlock block : visionText.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            lineasDetectadas.add(line.getText());
                        }
                    }

                    evaluarYAnunciar(lineasDetectadas);

                    bitmap.recycle();
                    procesandoOCR = false;
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error ML Kit OCR: " + e.getMessage());
                    bitmap.recycle();
                    procesandoOCR = false;
                });
    }

    private void evaluarYAnunciar(List<String> textos) {
        if (textos.isEmpty()) return;

        // 1. Verificar que la pantalla sea una solicitud real
        boolean esSolicitudActiva = false;
        Pattern patronServicio = Pattern.compile("d.di\\s*moto|arrendamientos?|aceptar", Pattern.CASE_INSENSITIVE);

        for (String t : textos) {
            if (patronServicio.matcher(t).find()) {
                esSolicitudActiva = true;
                break;
            }
        }

        if (!esSolicitudActiva) {
            return;
        }

        int tarifa = 0;
        int arrendamientos = -1;
        double distanciaTotalKm = 0.0;

        // 2. Extraer tarifa principal aislada por bloque
        String textoUnido = TextUtils.join(" | ", textos).replace("$ | ", "$");
        Pattern patronTarifa = Pattern.compile("\\$\\s*([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]{4,6})");
        Matcher mTarifa = patronTarifa.matcher(textoUnido);

        while (mTarifa.find()) {
            int finBloque = textoUnido.indexOf("|", mTarifa.end());
            if (finBloque == -1) finBloque = textoUnido.length();

            String mismoBloque = textoUnido.substring(mTarifa.start(), finBloque).toLowerCase();
            String grupoMonto = mTarifa.group(1);

            if (grupoMonto != null && !mismoBloque.contains("dinámica") && !mismoBloque.contains("servicio")) {
                String limpio = grupoMonto.replaceAll("[^0-9]", "");
                try {
                    int valor = Integer.parseInt(limpio);
                    if (valor >= 3000) {
                        tarifa = valor;
                        break;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 3. Extraer arrendamientos y distancia
        Pattern patronArrendamientos = Pattern.compile("(\\d+)\\s*arrendamientos?", Pattern.CASE_INSENSITIVE);
        Pattern patronDistancia = Pattern.compile("\\((\\d+(?:[.,]\\d+)?)\\s*(km|m)\\)", Pattern.CASE_INSENSITIVE);

        for (String texto : textos) {
            String textoMin = texto.toLowerCase();

            if (arrendamientos == -1) {
                Matcher mArrend = patronArrendamientos.matcher(texto);
                if (mArrend.find()) {
                    String grupoArrend = mArrend.group(1);
                    if (grupoArrend != null) {
                        try {
                            arrendamientos = Integer.parseInt(grupoArrend);
                        } catch (NumberFormatException ignored) {}
                    }
                } else if (textoMin.contains("nuevo")) {
                    arrendamientos = 0;
                }
            }

            Matcher mDist = patronDistancia.matcher(texto);
            if (mDist.find()) {
                String grupoNum = mDist.group(1);
                String grupoUnidad = mDist.group(2);
                if (grupoNum != null && grupoUnidad != null) {
                    String numStr = grupoNum.replace(",", ".");
                    String unidad = grupoUnidad.toLowerCase();
                    try {
                        double valor = Double.parseDouble(numStr);
                        if (unidad.equals("m")) {
                            distanciaTotalKm += (valor / 1000.0);
                        } else {
                            distanciaTotalKm += valor;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // 4. Leer parámetros configurados en MainActivity
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        int tarifaMinima = prefs.getInt(MainActivity.KEY_TARIFA, 12000);
        int valorMinKm = prefs.getInt(MainActivity.KEY_VALOR_KM, 900);
        int minArrendamientos = prefs.getInt(MainActivity.KEY_ARRENDAMIENTOS, 5);
        int pesosPorKm = (distanciaTotalKm > 0) ? (int) (tarifa / distanciaTotalKm) : 0;
        Log.d(TAG, String.format("Parsed -> Tarifa: $%d (Min: %d) | Arrend: %d (Min: %d) | $/km: $%d (Min: %d)",
                tarifa, tarifaMinima, arrendamientos, minArrendamientos, pesosPorKm, valorMinKm));
// 5. Evaluar condiciones y anunciar por voz con el formato original
        boolean cumpleTarifa = (tarifa >= tarifaMinima);
        boolean cumpleKm = (distanciaTotalKm == 0) || (pesosPorKm >= valorMinKm);
        if (cumpleTarifa && cumpleKm) {
            String firmaOferta = tarifa + "_" + arrendamientos;
            long ahora = System.currentTimeMillis();
            if (!firmaOferta.equals(ultimaOfertaAnunciada) || (ahora - ultimoTiempoAnuncio) > 15000) {
                ultimaOfertaAnunciada = firmaOferta;
                ultimoTiempoAnuncio = ahora;
                String infoSeguridad;
                if (arrendamientos == -1) {
                    infoSeguridad = "Sin datos de viajes";
                } else if (arrendamientos < minArrendamientos) {
                    infoSeguridad = "Precaución, solo " + arrendamientos + " arrendamientos";
                } else {
                    infoSeguridad = arrendamientos + " arrendamientos";
                }
                String infoKm = "";
                if (distanciaTotalKm > 0) {
                    infoKm = String.format(Locale.US, ". %.1f kilómetros, a %d por kilómetro", distanciaTotalKm, pesosPorKm);
                }
                String textoTarifa = tarifa + " pesos";
                String mensaje = textoTarifa + ". " + infoSeguridad + infoKm;

                Log.i(TAG, "ANUNCIANDO POR VOZ: " + mensaje);
                if (tts != null) {
                    tts.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, "ID_OFERTA");
                }
            }
        }
    }
    @Override
    public void onDestroy() {
        if (handlerVigilancia != null && tareaVigilancia != null) {
            handlerVigilancia.removeCallbacks(tareaVigilancia);
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (textRecognizer != null) {
            textRecognizer.close();
        }
        super.onDestroy();
    }
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Servicio interrumpido");
    }
    private void apagarPantalla() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            boolean exito = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            Log.d(TAG, "Pantalla apagada automáticamente: " + exito);
        }
    }

}