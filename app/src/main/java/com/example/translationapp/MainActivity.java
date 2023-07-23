package com.example.translationapp;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;
    private String languagePrompt = "Spanish";
    public synchronized void setLanguagePrompt(String languagePrompt){
        this.languagePrompt = languagePrompt;
    }
    public synchronized String getLanguagePrompt(){
        return languagePrompt;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cleanupOldImages();
        //create listener for setLanguage button
        Button buttonSetLanguage = findViewById(R.id.button_set_language);

        buttonSetLanguage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextInputEditText textInputEditText = findViewById(R.id.language_textbox);
                String inputText = textInputEditText.getText().toString();
                if(inputText != null && !inputText.isEmpty()){
                    setLanguagePrompt(inputText);
                }
                else{
                    setLanguagePrompt("Spanish");
                }


            }
        });
        Button buttonCamera = findViewById(R.id.button_camera);
        buttonCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.CAMERA}, 100);
                }
                else{
                    openCamera();
                }

            }
        });
    }

    //cleanup old images

    private void cleanupOldImages() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        long currentTime = System.currentTimeMillis();

        long ageLimit = 1000 * 60 * 60 * 24; // 1 day
        File[] files = dir.listFiles();
        if(files != null) {
            for (File file : files) {
                // If the file is an image file and it's older than the age limit
                if (file.getName().endsWith(".jpg") && currentTime - file.lastModified() > ageLimit) {
                    // Delete the file
                    boolean deleted = file.delete();
                    if (!deleted) {
                        Log.e("MainActivity", "Failed to delete old image file: " + file.getAbsolutePath());
                    }
                }
            }
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Handle the error
            }
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(this,
                        "com.example.translationapp.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePictureLauncher.launch(takePictureIntent);
            }
        }
    }
    private Uri photoURI;


    // Create an image file name
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return image;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to use the camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final ActivityResultLauncher<Intent> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        ImageView imageView = findViewById(R.id.image_view);
                        //starting new activity
                        Intent processPictureIntent = new Intent(MainActivity.this, ImageProcessingActivity.class);
                        processPictureIntent.putExtra("imageUri", photoURI.toString());
                        processPictureIntent.putExtra("language", getLanguagePrompt());
                        startActivity(processPictureIntent);
                    } else {

                    }
                }
            });
    // Use MlKit to draw boxes around text in the image

    private final ActivityResultLauncher<Intent> takePictureLauncherOLD = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {

                        try {
                            // Load image as a bitmap
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photoURI);
                            InputStream inputStream = getContentResolver().openInputStream(photoURI);
                            ExifInterface exif = new ExifInterface(inputStream);
                            int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                            int rotationInDegrees = exifToDegrees(rotation);

                            Matrix matrix = new Matrix();
                            matrix.postRotate(rotationInDegrees);
                            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                            // Create an InputImage
                            InputImage image = InputImage.fromFilePath(MainActivity.this, photoURI);

                            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                            Task<Text> resultText = recognizer.process(image)
                                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                                        @Override
                                        public void onSuccess(Text visionText) {
                                            Bitmap mutableBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true);

                                            Canvas canvas = new Canvas(mutableBitmap);

                                            Paint paint = new Paint();
                                            paint.setColor(Color.RED);
                                            paint.setStyle(Paint.Style.STROKE);
                                            paint.setStrokeWidth(5f);

                                            for (Text.TextBlock block : visionText.getTextBlocks()) {
                                                for (Text.Line line : block.getLines()) {
                                                    for (Text.Element element : line.getElements()) {
                                                        canvas.drawRect(element.getBoundingBox(), paint);
                                                    }
                                                }
                                            }
                                            ImageView imageView = findViewById(R.id.image_view);
                                            imageView.setImageBitmap(mutableBitmap);
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

                            ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
                            labeler.process(image)
                                    .addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                                        @Override
                                        public void onSuccess(List<ImageLabel> imageLabels) {
                                            for (ImageLabel label : imageLabels) {
                                                String text = label.getText();
                                                float confidence = label.getConfidence();
                                                int index = label.getIndex();
                                                Log.d("MainActivity", "Label: " + text + " Confidence: " + confidence + " Index: " + index);
                                            }
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Log.e("MainActivity", "Error during label detection: " + e);
                                        }
                                    });


                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }



                        // TODO: Use the bitmap as needed
                    }
                }
            }
    );

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