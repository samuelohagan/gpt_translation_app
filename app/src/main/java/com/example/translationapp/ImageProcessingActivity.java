package com.example.translationapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.graphics.Outline;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ImageProcessingActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private HashMap<String, Rect> globalWordsBoundingBox = new HashMap<>();
    private HashMap<String, String> translations = new HashMap<>();
    private Canvas canvas;
    private Bitmap mutableBitmap, overlayBitmap;
    private ImageView imageView;
    private ProgressBar progressBar;

    private synchronized void addTranslations(String originalWord, String translatedWord) {
        translations.put(originalWord, translatedWord);
    }
    private synchronized String getTranslation(String originalWord) {
        return translations.get(originalWord);
    }
    private synchronized List<String> getAllTranslations() {
        return new ArrayList<>(translations.keySet());
    }
    private synchronized int getNumTranslations() {
        return translations.size();
    }

    private synchronized void addWord(String word, Rect boundingBox) {
        globalWordsBoundingBox.put(word, boundingBox);
    }

    private synchronized void removeWords(String words) {
        globalWordsBoundingBox.remove(words);
    }
    private synchronized List<String> viewAllWords() {
        return new ArrayList<>(globalWordsBoundingBox.keySet());
    }
    private synchronized List<Rect> getAllBoundingBox() {
        List<Rect> boundingBoxes = new ArrayList<>();
        for(String key : globalWordsBoundingBox.keySet()) {
            boundingBoxes.add(globalWordsBoundingBox.get(key));
        }
        return boundingBoxes;

    }
    private void drawOnImage() {


        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        List<Rect> boxes = getAllBoundingBox();
        // Assume boxes is a list of bounding boxes to draw.
        for (Rect box : boxes) {
            canvas.drawRect(box, paint);
        }

        Bitmap combined = combineBitmaps(mutableBitmap, overlayBitmap);
        imageView.setImageBitmap(combined);
    }

    private Canvas drawTextOnImage(Canvas textCanvas, HashMap<String, Rect> translatedBoundingBox) {
        TextPaint textPaint = new TextPaint();
        textPaint.setColor(Color.BLACK);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(100f);

        Paint blackOverlay = new Paint();
        blackOverlay.setColor(Color.WHITE);
        blackOverlay.setAlpha(190);

        for (String word : translatedBoundingBox.keySet()) {
            Rect boundingBox = translatedBoundingBox.get(word);
            RectF rect = new RectF(boundingBox);
            textCanvas.drawRect(rect, blackOverlay);
            StaticLayout staticLayout = new StaticLayout(word, textPaint, (int) rect.width(), Layout.Alignment.ALIGN_NORMAL, 1, 1, false);
            while ((staticLayout.getHeight() > rect.height()) && (textPaint.getTextSize() > 4)) {
                textPaint.setTextSize(textPaint.getTextSize() - 2);
                staticLayout = new StaticLayout(word, textPaint, (int) rect.width(), Layout.Alignment.ALIGN_NORMAL, 1, 1, false);
            }
            textCanvas.save();
            textCanvas.translate(rect.left, rect.top);
            staticLayout.draw(textCanvas);
            textCanvas.restore();
        }
        return textCanvas;
    }
    private Bitmap combineBitmaps(Bitmap background, Bitmap overlay) {
        Bitmap combined = Bitmap.createBitmap(background.getWidth(), background.getHeight(), background.getConfig());
        Canvas combinedCanvas = new Canvas(combined);
        combinedCanvas.drawBitmap(background, 0f, 0f, null);
        combinedCanvas.drawBitmap(overlay, 0f, 0f, null);
        return combined;
    }
    private void performTranslation(String language) {
        List<String> wordsToTranslate = viewAllWords();


        if (wordsToTranslate.isEmpty()) {
            Toast.makeText(this, "No words to translate", Toast.LENGTH_SHORT).show();
            return;
        }
        else{
            progressBar = findViewById(R.id.progress_bar);
            progressBar.setVisibility(View.VISIBLE);
            List<String> wordsToTranslateLocal = wordsToTranslate;
            OpenAiManager manager = new OpenAiManager();
            AtomicInteger requestsRemaining = new AtomicInteger(wordsToTranslateLocal.size());
            for(String word : wordsToTranslateLocal) {
                manager.callGptTranslation(word, language,  new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        // Handle the failure here
                        if (requestsRemaining.decrementAndGet() == 0) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setVisibility(View.GONE);
                                }
                            });
                        }

                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            System.out.println(responseBody);
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                JsonNode jsonNode = mapper.readTree(responseBody);

                                // Get the content field from the "message" object
                                JsonNode messageNode = jsonNode.at("/choices/0/message");
                                String content = messageNode.get("content").asText();
                                addTranslations(word, content);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            // Process the response body here
                        } else {
                            // Handle unsuccessful response here
                        }
                        if (requestsRemaining.decrementAndGet() == 0) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setVisibility(View.GONE);
                                }
                            });
                        }
                    }
                });
            }



        }
        System.out.println("test");

    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_processing);
        String language = getIntent().getStringExtra("language");
        imageView = findViewById(R.id.image_view);
        Button selectWordsButton = findViewById(R.id.select_words_button);
        Button selectTranslateButton = findViewById(R.id.translate_button);
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setVisibility(View.GONE);
        selectWordsButton.setOnClickListener(v -> {
            // Toggle visibility of the ImageView and RecyclerView
            if (imageView.getVisibility() == View.VISIBLE) {
                imageView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.GONE);
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                drawOnImage();
                HashMap<String, Rect> translatedWords = new HashMap<>();
                if (getNumTranslations() > 0) {
                    for (String word : getAllTranslations()) {
                        String translatedWord = getTranslation(word);
                        Rect boundingBox = globalWordsBoundingBox.get(word);
                        translatedWords.put(translatedWord, boundingBox);
                    }
                }
                canvas = drawTextOnImage(canvas, translatedWords);
                drawOnImage();
                imageView.setVisibility(View.VISIBLE);

            }
        });
        selectTranslateButton.setOnClickListener(v ->{
            performTranslation(language);
        });
        String photoURIString = getIntent().getStringExtra("imageUri");

        final Uri photoUri = Uri.parse(photoURIString);
        executor.execute(() -> {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photoUri);
                Bitmap rotatedBitmap = rotateImageIfRequired(bitmap, photoUri);
                //make this resolve if bitmap is null
                if (bitmap != null) {
                    handler.post(() -> imageView.setImageBitmap(rotatedBitmap));
                }
                TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                InputImage image = InputImage.fromBitmap(rotatedBitmap, 0);

                Task<Text> resultText = recognizer.process(image)
                        .addOnSuccessListener(new OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text visionText) {
                                HashMap<String, Rect> words = new HashMap<>();
                                mutableBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true);
                                overlayBitmap = Bitmap.createBitmap(mutableBitmap.getWidth(), mutableBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                                canvas = new Canvas(overlayBitmap);

                                for (Text.TextBlock block : visionText.getTextBlocks()) {
                                    List<String> linesInBlock = new ArrayList<>();
                                    for (Text.Line line : block.getLines()) {
                                        StringBuilder sb = new StringBuilder();
                                        for (Text.Element element : line.getElements()) {
                                            sb.append(element.getText() + (" "));
                                        }
                                        linesInBlock.add(sb.toString());
                                    }
                                    String blockText = String.join(" | ", linesInBlock);
                                    words.put(blockText, block.getBoundingBox());
                                }
                                drawOnImage();


                                runOnUiThread(() -> {
                                    //RecyclerView recyclerView = findViewById(R.id.recycler_view);
                                    recyclerView.setLayoutManager(new LinearLayoutManager(ImageProcessingActivity.this));
                                    List<String> wordList = words.keySet().stream().collect(Collectors.toList());
                                    WordsAdapter adapter = new WordsAdapter(wordList, (word, isSelected) -> {
                                        if (isSelected) {
                                            globalWordsBoundingBox.put(word, words.get(word));;

                                        } else {
                                            globalWordsBoundingBox.remove(word);

                                        }
                                    });
                                    recyclerView.setAdapter(adapter);

                                });

                            }
                        })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        // Task failed with an exception
                                        // ...
                                    }
                                });
            } catch (IOException e) {
                e.printStackTrace();
                //handle error, show error message
                handler.post(() -> {
                    Toast.makeText(ImageProcessingActivity.this, "Error loading image", Toast.LENGTH_SHORT).show();
                });
            }
        });

    }
    public class WordsAdapter extends RecyclerView.Adapter<WordsAdapter.WordViewHolder> {
        private final List<String> words;
        private final OnWordClickListener onWordClickListener;
        private Set<String> selectedWords;

        public WordsAdapter(List<String> words, OnWordClickListener onWordClickListener) {
            this.words = words;
            this.selectedWords = new HashSet<>();
            this.onWordClickListener = onWordClickListener;
        }

        @NonNull
        @Override
        public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new WordViewHolder(view, onWordClickListener);
        }


        @Override
        public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
            holder.bind(words.get(position), position);
        }

        @Override
        public int getItemCount() {
            return words.size();
        }

        interface OnWordClickListener {
            void onWordClick(String word, boolean isSelected);
        }

        class WordViewHolder extends RecyclerView.ViewHolder{
            TextView wordTextView;
            OnWordClickListener onWordClickListener;

            public WordViewHolder(@NonNull View itemView, OnWordClickListener onWordClickListener) {
                super(itemView);
                wordTextView = itemView.findViewById(android.R.id.text1);
                this.onWordClickListener = onWordClickListener;
            }
            public void bind(String word, int position){
                wordTextView.setText(word);
                wordTextView.setBackgroundColor(selectedWords.contains(words.get(position)) ? Color.YELLOW : Color.TRANSPARENT);
                wordTextView.setOnClickListener(v -> {
                    if (selectedWords.contains(words.get(position))) {
                        selectedWords.remove(word);
                        wordTextView.setBackgroundColor(Color.TRANSPARENT);
                        onWordClickListener.onWordClick(word, false);
                    } else {
                        selectedWords.add(word);
                        wordTextView.setBackgroundColor(Color.YELLOW);
                        onWordClickListener.onWordClick(word, true);
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        //clean up anything that needs to be cleaned up
    }


    //rotate bitmap using exif information
    private Bitmap rotateImageIfRequired(Bitmap bitmap, Uri photoUri) throws IOException {
        InputStream photoInputStream = this.getContentResolver().openInputStream(photoUri);
        ExifInterface exif = new ExifInterface(photoInputStream);
        int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        int rotationInDegrees = exifToDegrees(rotation);

        if (rotationInDegrees == 0) {
            return bitmap;
        } else {
            return rotateBitmap(bitmap, rotationInDegrees);
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        matrix.postRotate(orientation);
        return Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
    }

    private static int exifToDegrees(int exifOrientation) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }


}