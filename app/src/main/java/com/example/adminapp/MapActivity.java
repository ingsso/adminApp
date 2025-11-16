package com.example.adminapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.label.Label;
import com.kakao.vectormap.label.LabelLayer;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MapActivity extends AppCompatActivity {
    private final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private final String[] locationPermissions = {android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng startPosition = null;
    private MapView mapView;
    private boolean requestingLocationUpdates = false;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private DatabaseReference databaseReference;

    private KakaoMap kakaoMapInstance;
    private final Map<String, Label> markerMap = new HashMap<>(); // 마커를 관리할 맵

    LabelLayer layer;

    Button btn_back, btn_location;

    private final KakaoMapReadyCallback readyCallback = new KakaoMapReadyCallback() {
        @Override
        public void onMapReady(@NonNull KakaoMap kakaoMap) {
            kakaoMapInstance = kakaoMap;

            ProgressBar progressBar = findViewById(R.id.progressBar);
            progressBar.setVisibility(View.GONE);

            layer = kakaoMap.getLabelManager().getLayer();

            // 위치 업데이트 시작
            startLocationUpdates();

            kakaoMap.setOnLabelClickListener((kakaoMap1, layer, label) -> {
                Object tag = label.getTag();
                Log.d("LabelClick", "Clicked Label ID: " + tag);

                if (tag == null) {
                    Log.e("LabelClick", "Label ID is null");
                    return; // labelId가 null인 경우 다이얼로그를 표시하지 않음
                }

                String labelId = tag.toString();
                databaseReference.child(labelId).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        String imageBase64 = dataSnapshot.child("imageData").getValue(String.class);
                        if (imageBase64 != null) {
                            showLocationDialog(imageBase64);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e("Firebase", "Database error: " + databaseError.getMessage());
                    }
                });
            });

            // 전달받은 orgPath에 있는 위치 데이터 가져오기
            String obstacle_path = getIntent().getStringExtra("obstacle_path");
            databaseReference = FirebaseDatabase.getInstance().getReference(obstacle_path);
            databaseReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        Double latitude = snapshot.child("latitude").getValue(Double.class);
                        Double longitude = snapshot.child("longitude").getValue(Double.class);
                        if (latitude != null && longitude != null) {
                            LatLng position = LatLng.from(latitude, longitude);
                            addMarker(kakaoMap, position, snapshot.getKey());
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e("Firebase", "Database error: " + databaseError.getMessage());
                }
            });
        }

        @NonNull
        @Override
        public LatLng getPosition() {
            return startPosition;
        }

        @Override
        public int getZoomLevel() {
            return 18;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.map_view);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {}
        };

        if (ContextCompat.checkSelfPermission(this, locationPermissions[0]) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, locationPermissions[1]) == PackageManager.PERMISSION_GRANTED) {
            getStartLocation();
        } else {
            ActivityCompat.requestPermissions(this, locationPermissions, LOCATION_PERMISSION_REQUEST_CODE);
        }

        btn_location = findViewById(R.id.btn_location);
        btn_location.setOnClickListener(v -> getStartLocation());

        btn_back = findViewById(R.id.btn_back);
        btn_back.setOnClickListener(v -> {
            Intent intent = new Intent(MapActivity.this, MainActivity.class);
            startActivity(intent);
        });
    }

    private void refreshMarkers(KakaoMap kakaoMap) {
        // 모든 마커 제거
        for (Label label : markerMap.values()) {
            layer.remove(label);
        }
        markerMap.clear();

        // 데이터베이스에서 최신 마커를 가져와 추가
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Double latitude = snapshot.child("latitude").getValue(Double.class);
                    Double longitude = snapshot.child("longitude").getValue(Double.class);

                    if (latitude != null && longitude != null) {
                        LatLng position = LatLng.from(latitude, longitude);
                        addMarker(kakaoMap, position, snapshot.getKey());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("Firebase", "Database error: " + databaseError.getMessage());
            }
        });
    }

    private void addMarker(@NonNull KakaoMap kakaoMap, LatLng position, String key) {
        layer = kakaoMap.getLabelManager().getLayer();
        if (layer == null) {
            Log.e("MapActivity", "LabelLayer is null when adding marker");
            return;
        }

        LabelOptions labelOptions = LabelOptions.from("marker" + key, position)
                .setTag(key)
                .setStyles(LabelStyle.from(R.drawable.location_marker).setAnchorPoint(0.5f, 0.5f))
                .setRank(1);

        Label label = layer.addLabel(labelOptions);

        if (label == null) {
            Log.e("MapActivity", "Failed to add marker: Label is null");
        } else {
            markerMap.put(key, label);
        }
    }

    private void showLocationDialog(String imageBase64) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_image_view);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawableResource(android.R.color.transparent);

        Button btn_close = dialog.findViewById(R.id.btn_close);
        ImageView location_image = dialog.findViewById(R.id.dialog_image_view);

        if (location_image == null) {
            Log.e("DialogDebug", "ImageView is null");
            return;
        }

        // Base64 문자열을 디코딩하여 Bitmap으로 변환
        byte[] decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT);
        Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

        // ImageView에 Bitmap 설정
        location_image.setImageBitmap(decodedBitmap);
        dialog.show();

        btn_close.setOnClickListener(v -> {
            refreshMarkers(kakaoMapInstance);
            dialog.dismiss();
        });
    }

    @Override    protected void onResume() {
        super.onResume();
        if (requestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @SuppressLint("MissingPermission")
    private void getStartLocation() {
        String obstacle_path = getIntent().getStringExtra("obstacle_path");
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(obstacle_path);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double sumLat = 0.0;
                double sumLng = 0.0;
                int count = 0;

                for (DataSnapshot child : snapshot.getChildren()) {
                    Double lat = child.child("latitude").getValue(Double.class);
                    Double lng = child.child("longitude").getValue(Double.class);

                    if (lat != null && lng != null) {
                        sumLat += lat;
                        sumLng += lng;
                        count++;
                    }
                }

                if (count > 0) {
                    double avgLat = sumLat / count;
                    double avgLng = sumLng / count;
                    startPosition = LatLng.from(avgLat, avgLng);
                    mapView.start(readyCallback); // 지도 시작
                } else {
                    Log.e("MapInit", "No obstacle locations found.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Failed to load obstacle locations: " + error.getMessage());
            }
        });
    }


    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        requestingLocationUpdates = true;
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
    }

    // 사용자가 권한 요청에 응답 시 실행
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) { // 위치 권한을 허용한 경우
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getStartLocation();
            } else { // 위치 권한을 거부한 경우
                showPermissionDeniedDialog();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("위치 권한 거부시 앱을 사용할 수 없습니다.")
                .setPositiveButton("권한 설정 하러 가기", (dialogInterface, i) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        e.printStackTrace();
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                        startActivity(intent);
                    } finally {
                        finish();
                    }
                })
                .setNegativeButton("앱 종료 하기", (dialogInterface, i) -> finish())
                .setCancelable(false)
                .show();
    }
}