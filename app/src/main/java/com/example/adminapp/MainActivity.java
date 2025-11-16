package com.example.adminapp;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import com.github.davidmoten.geo.GeoHash;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "org_prefs";
    private static final String PREF_ORG_PATH = "org_path";
    private static final String PREF_TOKEN_SAVED = "token_saved";

    Button btn_map_tap, btn_org_set;
    TextView tv_org;

    View toast;
    Toast tst;
    TextView toast_text;

    private final List<String> first_org_list = new ArrayList<>();
    private ArrayAdapter<String> first_org_adapter;
    private final List<String> second_org_list = new ArrayList<>();
    private ArrayAdapter<String> second_org_adapter;
    private final List<String> third_org_list = new ArrayList<>();
    private ArrayAdapter<String> third_org_adapter;


    private SharedPreferences shared_preferences;

    private String full_path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        tv_org = findViewById(R.id.tv_org);

        shared_preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 저장된 org_path 값을 불러 와서 full_path 에 저장 하고, 텍스트 뷰에 표시
        full_path = shared_preferences.getString(PREF_ORG_PATH, null);
        if (full_path != null) {
            String[] path_parts = full_path.split("/");
            if (path_parts.length > 1) {
                String second_org = path_parts[1];
                tv_org.setText(second_org);
            }
        }

        first_org_adapter = new ArrayAdapter<>(this, R.layout.spinner_item, first_org_list);
        first_org_adapter.setDropDownViewResource(R.layout.spinner_item);
        second_org_adapter = new ArrayAdapter<>(this, R.layout.spinner_item, second_org_list);
        second_org_adapter.setDropDownViewResource(R.layout.spinner_item);
        third_org_adapter = new ArrayAdapter<>(this, R.layout.spinner_item, third_org_list);
        third_org_adapter.setDropDownViewResource(R.layout.spinner_item);

        toast = View.inflate(MainActivity.this, R.layout.toast_layout, null);
        tst = new Toast(MainActivity.this);
        tst.setView(toast);
        toast_text = toast.findViewById(R.id.tvToast);

        btn_map_tap = findViewById(R.id.btn_map_tap);
        btn_map_tap.setOnClickListener(v -> {
            String obstacle_path = "organizations/" + full_path;
            DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference(obstacle_path);
            databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                String geohash = "";

                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Map<String, Object> dataMap = (Map<String, Object>) snapshot.getValue();
                    if (dataMap != null && dataMap.containsKey("geohash")) {
                        geohash = (String) dataMap.get("geohash");
                    }

                    if (geohash != null) {
                        // geohash가 null이 아니라면 장애물 경로를 확인
                        String check_path = "obstacles/" + geohash;
                        checkLocationExists(check_path);
                        sendGeohashToService(geohash);
                    } else {
                        // geohash가 null이면 장애물이 없다는 메시지를 표시
                        toast_text.setText("발견된 장애물이 없습니다.");
                        tst.show();
                    }
                }


                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Database error: " + error.getMessage()); // 로그 추가
                }
            });
        });

        btn_org_set = findViewById(R.id.btn_org_set);
        btn_org_set.setOnClickListener(v -> showOrgSetDialog());
    }

    private void saveTokenToFirestore(String token, String geohash) {
        // 이미 토큰이 저장된 경우, 저장하지 않음
        boolean isTokenSaved = shared_preferences.getBoolean(PREF_TOKEN_SAVED, false);
        if (isTokenSaved) {
            Log.d(TAG, "Token already saved, skipping saving process.");
            return;
        }

        String deviceId = getDeviceUniqueId();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection(geohash)
                .document(deviceId)
                .set(new TokenData(token))
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Token saved successfully for geohash: " + geohash);
                    // 토큰 저장 완료 후, SharedPreferences에 저장된 상태 업데이트
                    SharedPreferences.Editor editor = shared_preferences.edit();
                    editor.putBoolean(PREF_TOKEN_SAVED, true);
                    editor.apply();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error saving token: " + e.getMessage()));
    }

    private String getDeviceUniqueId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    // 기관 설정 후 토큰을 저장하는 메서드
    private void sendGeohashToService(String geohash) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String token = task.getResult();
                saveTokenToFirestore(token, geohash);
            } else {
                Log.e(TAG, "Error getting token", task.getException());
            }
        });
    }

    // 기관 설정을 변경했을 때 토큰을 다시 저장하지 않도록 하는 메서드
    private void showOrgSetDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.org_dialog);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawableResource(android.R.color.transparent);

        Button btn_check = dialog.findViewById(R.id.btn_check);
        Button btn_close = dialog.findViewById(R.id.btn_close);
        Spinner first_spinner = dialog.findViewById(R.id.first_spinner);
        Spinner second_spinner = dialog.findViewById(R.id.second_spinner);
        Spinner third_spinner = dialog.findViewById(R.id.third_spinner);

        first_org_list.clear();
        first_org_list.add("시/도");
        second_org_list.clear();
        second_org_list.add("시/구/군");
        third_org_list.clear();
        third_org_list.add("동");

        first_spinner.setAdapter(first_org_adapter);
        first_spinner.setSelection(0);
        first_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    String selected_org = first_org_list.get(position);
                    fetchSecondOrganizations(selected_org);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                setToastText();
            }
        });

        second_spinner.setAdapter(second_org_adapter);
        second_spinner.setSelection(0);
        second_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    String first_org = first_org_list.get(first_spinner.getSelectedItemPosition());
                    Log.d(TAG, "first_org = " + first_org);
                    String selected_org = second_org_list.get(position);
                    fetchThirdOrganizations(first_org, selected_org);
//                    tv_org.setText(selected_org);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                setToastText();
            }
        });

        third_spinner.setAdapter(third_org_adapter);
        third_spinner.setSelection(0);
        third_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    String selected_org = third_org_list.get(position);
                    tv_org.setText(selected_org);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        btn_check.setOnClickListener(v -> {
            int third_position = third_spinner.getSelectedItemPosition();

            if (third_position > 0) {
                full_path = first_org_list.get(first_spinner.getSelectedItemPosition()) + "/"
                        + second_org_list.get(second_spinner.getSelectedItemPosition()) + "/"
                        + third_org_list.get(third_spinner.getSelectedItemPosition());

                Log.d(TAG, "Selected org path = " + full_path);

                setSharedPreferences(full_path);

                // Firebase에서 orgPath에 해당하는 geohash 값을 불러옴
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("organizations").child(full_path);
                ref.child("geohash").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String geohash = snapshot.getValue(String.class);
                            Log.d(TAG, "Fetched geohash from orgPath: " + geohash);
                            if (!shared_preferences.getBoolean(PREF_TOKEN_SAVED, false)) {
                                sendGeohashToService(geohash);
                            }
                        } else {
                            Log.e(TAG, "No geohash found for selected orgPath.");
                            toast_text.setText("선택한 지역에 지오해시 정보가 없습니다.");
                            tst.show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to fetch geohash: " + error.getMessage());
                        toast_text.setText("지오해시 불러오기 실패");
                        tst.show();
                    }
                });

                dialog.dismiss();
            } else {
                setToastText();
            }
        });


        btn_close.setOnClickListener(v -> {
            int position = third_spinner.getSelectedItemPosition();

            if (position > 0 || full_path != null) {
                dialog.dismiss();
            } else {
                setToastText();
            }
        });

        fetchFirstOrganizations();
        dialog.show();
    }

    // SharedPreferences에 기관 경로를 저장하는 메서드
    private void setSharedPreferences(String org_path) {
        SharedPreferences.Editor editor = shared_preferences.edit();
        editor.putString(PREF_ORG_PATH, org_path);
        editor.apply();
    }

    private void setToastText () {
        toast_text.setText("지역을 선택하세요.");
        tst.show();
    }

    private void fetchFirstOrganizations() {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("organizations");
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                first_org_list.clear(); // 중복 방지를 위해 clear
                first_org_list.add("시/도");
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String org_key = snapshot.getKey();
                    first_org_list.add(org_key);
                }
                first_org_adapter.notifyDataSetChanged(); // 어댑터에 데이터 변경 사항 알림
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error fetch_first_organizations", databaseError.toException());
            }
        });
    }

    private void fetchSecondOrganizations(String first_org) {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("organizations").child(first_org);
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                second_org_list.clear(); // 중복 방지를 위해 clear
                second_org_list.add("시/구/군");
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String second_org_key = snapshot.getKey();
                    second_org_list.add(second_org_key);
                }
                second_org_adapter.notifyDataSetChanged(); // 어댑터에 데이터 변경 사항 알림
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error fetch_second_organizations", databaseError.toException());
            }
        });
    }

    private void fetchThirdOrganizations(String first_org, String second_org) {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("organizations").child(first_org).child(second_org);
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                third_org_list.clear(); // 중복 방지를 위해 clear
                third_org_list.add("동");
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String third_org_key = snapshot.getKey();
                    third_org_list.add(third_org_key);
                }
                third_org_adapter.notifyDataSetChanged(); // 어댑터에 데이터 변경 사항 알림
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error fetch_third_organizations", databaseError.toException());
            }
        });
    }

    private void getCurrentLocationAndSaveGeohash(String orgPath) {
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();

                String geohash = GeoHash.encodeHash(latitude, longitude, 6);
                Log.d(TAG, "Generated geohash: " + geohash);

                // Firebase에 저장
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("organizations").child(orgPath);
                ref.child("geohash").setValue(geohash).addOnSuccessListener(aVoid -> Log.d(TAG, "Geohash saved successfully.")).addOnFailureListener(e -> Log.e(TAG, "Failed to save geohash: " + e.getMessage()));

                if (!shared_preferences.getBoolean(PREF_TOKEN_SAVED, false)) {
                    sendGeohashToService(geohash);
                }
            } else {
                Log.e(TAG, "Location is null.");
            }
        });
    }


    private void checkLocationExists(String obstacle_path) {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference(obstacle_path);
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                boolean locationExists = false;

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {

                    if (Objects.requireNonNull(snapshot.getKey()).startsWith("cctv")) {
                        locationExists = true;
                        break;
                    }
                }
                if (locationExists) {
                    Intent map_intent = new Intent(MainActivity.this, MapActivity.class);
                    map_intent.putExtra("obstacle_path", obstacle_path);
                    startActivity(map_intent);
                } else {
                    toast_text.setText("접수된 장애물이 없습니다.");
                    tst.show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Database error: " + databaseError.getMessage()); // 로그 추가
                toast_text.setText("데이터베이스 오류가 발생했습니다.");
                tst.show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 권한이 승인되었으면 다시 시도
            if (full_path != null) {
                getCurrentLocationAndSaveGeohash(full_path);
            }
        } else {
            toast_text.setText("위치 권한이 필요합니다.");
            tst.show();
        }
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAffinity();
    }
}