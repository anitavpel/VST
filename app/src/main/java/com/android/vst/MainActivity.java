package com.android.vst;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private EditText mResultEt, fileNameEditText;
    private ImageView mPreviewIv;
    String fileName;
    private File filePath;


    //Permission Code
    private static final int CAMERA_REQUEST_CODE = 200;
    private static final int STORAGE_REQUEST_CODE = 400;
    private static final int IMAGE_PICK_GALLERY_CODE = 1000;
    private static final int IMAGE_PICK_CAMERA_CODE = 2001;

    List<Rect> rectangleList = new ArrayList<>();


    private Bitmap originalBitmap; // Original image bitmap
    private Bitmap tempBitmap; // Temporary bitmap for drawing rectangles
    private Canvas canvas; // Canvas for drawing on the temporary bitmap
    private Paint paint; // Paint for drawing rectangles
    private Rect cropRect; // Rectangle for cropping the image


    String cameraPermission[];
    String storagePermission[];


    Uri imageUri;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE}, PackageManager.PERMISSION_GRANTED);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setSubtitle("Click + button to insert image");

        mResultEt = findViewById(R.id.resultEt);
        mPreviewIv = findViewById(R.id.imageIv);
        fileNameEditText = findViewById(R.id.file_name_edit_text);


        originalBitmap = null;
        tempBitmap = null;
        canvas = null;
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        cropRect = new Rect();


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate( R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.addImage:
                showImageImportDialog();
                break;

            case R.id.about:
                dialogAbout();
                break;
        }
        return true;
    }

    private void dialogAbout() {
        new AlertDialog.Builder(this)
                .setTitle("About App")
                .setMessage("This app is made by Virtuoso SoftTech.")
                .setPositiveButton("CLOSE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void showImageImportDialog() {
        String[] items = {"Camera", "Gallery"};
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);

        dialog.setTitle("Select Image");
        dialog.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    if (!checkCameraPermission()) {
                        // Camera permission not allowed, request it
                        requestCameraPermission();
                    } else {
                        // Permission allowed, take picture
                        pickCamera();
                    }

                }

                if (which == 1) {
                    if (!checkStoragePermission()) {
                        // Storage permission not allowed, request it
                        requestStoragePermission();
                    } else {
                        // Permission allowed, pick from gallery
                        pickGallery();
                    }
                }
            }
        });
        dialog.create().show();


    }

    private void pickGallery() {
        // Intent to pick image from gallery
        Intent intent = new Intent(Intent.ACTION_PICK);
        // Set intent type to image
        intent.setType("image/*");
        startActivityForResult(intent, IMAGE_PICK_GALLERY_CODE);
    }

    private void pickCamera() {
        // Intent to take image from camera, it will also be saved to storage to get high-quality image
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "NewPick"); // Title of the picture
        values.put(MediaStore.Images.Media.DESCRIPTION, "Image To Text"); // Description of the picture
        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(cameraIntent, IMAGE_PICK_CAMERA_CODE);
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this, storagePermission, STORAGE_REQUEST_CODE);
    }

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, cameraPermission, CAMERA_REQUEST_CODE);
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Handle permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CAMERA_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean writeStorageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

                    if (cameraAccepted && writeStorageAccepted) {
                        pickCamera();
                    } else {
                        Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
                break;

            case STORAGE_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean writeStorageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

                    if (writeStorageAccepted) {
                        pickGallery();
                    } else {
                        Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == IMAGE_PICK_GALLERY_CODE || requestCode == IMAGE_PICK_CAMERA_CODE) {
                Uri imageUri = data.getData();
                mPreviewIv.setImageURI(imageUri);
                try {
                    originalBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    tempBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.RGB_565);
                    canvas = new Canvas(tempBitmap);
                    canvas.drawBitmap(originalBitmap, 0, 0, null);

                    mPreviewIv.setImageBitmap(tempBitmap);

                    mPreviewIv.setOnTouchListener(new View.OnTouchListener() {
                        List<Rect> cropRects = new ArrayList<>();
                        float startX, startY, endX, endY;

                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if (v.getId() == R.id.imageIv) {
                                float touchX = event.getX();
                                float touchY = event.getY();

                                if (touchX >= 0 && touchX <= originalBitmap.getWidth() && touchY >= 0 && touchY <= originalBitmap.getHeight()) {
                                    switch (event.getAction()) {
                                        case MotionEvent.ACTION_DOWN:
                                            startX = touchX;
                                            startY = touchY;
                                            break;
                                        case MotionEvent.ACTION_MOVE:
                                            endX = touchX;
                                            endY = touchY;

                                            canvas.drawBitmap(originalBitmap, 0, 0, null);
                                            canvas.drawRect(startX, startY, endX, endY, paint);
                                            mPreviewIv.setImageBitmap(tempBitmap);
                                            break;
                                        case MotionEvent.ACTION_UP:

                                            if (startX != endX && startY != endY) {
                                                cropRects.add(new Rect(
                                                        Math.round(Math.min(startX, endX)),
                                                        Math.round(Math.min(startY, endY)),
                                                        Math.round(Math.max(startX, endX)),
                                                        Math.round(Math.max(startY, endY))
                                                ));


                                                canvas.drawBitmap(originalBitmap, 0, 0, null);
                                                mPreviewIv.setImageBitmap(tempBitmap);


                                                StringBuilder sb = new StringBuilder();
                                                for (Rect rect : cropRects) {

                                                    if (rect.width() > 0 && rect.height() > 0) {
                                                        Bitmap croppedBitmap = Bitmap.createBitmap(originalBitmap, rect.left, rect.top, rect.width(), rect.height());
                                                        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(croppedBitmap);
                                                        FirebaseVisionTextRecognizer textRecognizer = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
                                                        textRecognizer.processImage(firebaseVisionImage)
                                                                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                                                                    @Override
                                                                    public void onSuccess(FirebaseVisionText firebaseVisionText) {
                                                                        for (FirebaseVisionText.TextBlock textBlock : firebaseVisionText.getTextBlocks()) {
                                                                            sb.append(textBlock.getText()).append("\n");
                                                                        }
                                                                        mResultEt.setText(sb.toString());
                                                                    }
                                                                })
                                                                .addOnFailureListener(new OnFailureListener() {
                                                                    @Override
                                                                    public void onFailure(@NonNull Exception e) {
                                                                        Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                                    }
                                                                });
                                                    }
                                                }
                                            }
                                            break;
                                    }
                                }
                            }
                            return true;
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    public void buttonCreateExcel(View view) {


        String fileName = fileNameEditText.getText().toString().trim();


        if (fileName.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please enter a file name", Toast.LENGTH_SHORT).show();
            return;
        }

        filePath = new File(Environment.getExternalStorageDirectory() + "/ExcelFiles/" + fileName + ".xlsx");
        if (!filePath.getParentFile().exists()) {
            filePath.getParentFile().mkdir(); // Create the folder if it doesn't exist
        }

        if (filePath.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("A file with the same name already exists. Do you want to overwrite it?")
                    .setIcon(R.drawable.background)
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> createExcelFile(filePath))
                    .setNegativeButton(android.R.string.no, null).show();
        } else {
            createExcelFile(filePath);
        }
    }

    private void createExcelFile(File file) {
        HSSFWorkbook hssfWorkbook = new HSSFWorkbook();
        HSSFSheet hssfSheet = hssfWorkbook.createSheet("Invoice Sheet");

        String text = mResultEt.getText().toString().trim();
        String[] textParts = text.split("\\s+", 10);

        // Create the first row and put the first two words in the first cell
        HSSFRow firstRow = hssfSheet.createRow(0);
        HSSFCell cell1 = firstRow.createCell(0);
        cell1.setCellValue(textParts[0] + " " + textParts[1]);
        hssfSheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        // Create the second row and put the next three words in one cell
        HSSFRow secondRow = hssfSheet.createRow(1);
        HSSFCell cell2 = secondRow.createCell(0);
        cell2.setCellValue(textParts[2] + " " + textParts[3] + " " + textParts[4]);
        hssfSheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 2));

        // Create the third row and put the remaining text in one cell
        HSSFRow thirdRow = hssfSheet.createRow(2);
        HSSFCell cell3 = thirdRow.createCell(0);
        cell3.setCellValue(textParts[5]);

        // Create the fourth row and put the words from line 6 and line 10 in one cell
        HSSFRow fourthRow = hssfSheet.createRow(3);
        HSSFCell cell4 = fourthRow.createCell(0);
        cell4.setCellValue(textParts[9] + " " + textParts[37]); // assuming line 6 is the 10th line and line 10 is the 38th line
        hssfSheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 2));

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            hssfWorkbook.write(fileOutputStream);

            if (fileOutputStream != null) {
                fileOutputStream.flush();
                fileOutputStream.close();
                Toast.makeText(MainActivity.this, "Excel file created successfully", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Error creating Excel file", Toast.LENGTH_SHORT).show();
        }
    }
}