👷 adminApp (장애물 신고 관리자 앱)
본 프로젝트는 실시간 장애물 근접 경고 앱 (<- 여기에 사용자 앱 링크를 넣으세요)과 연동되는 관리자용 모니터링 앱입니다.

지역(기관) 관리자가 담당 구역을 설정하면, 해당 구역의 Geohash를 기반으로 Firebase에 등록된 장애물(CCTV) 신고 내역을 카카오맵에서 시각적으로 모니터링하고, **실시간 푸시 알림(FCM)**을 수신할 수 있습니다.

📱 앱 실행 데모 (GIF)
[관리자 앱이 실제로 동작하는 모습의 GIF를 여기에 삽입하세요]

(예: 관리자가 지역을 선택하고, 지도에서 신고 내역 마커와 이미지를 확인하는 모습)

🛠️ 사용된 핵심 기술
언어: Java

플랫폼: Android

지도: Kakao Maps SDK

데이터베이스:

Firebase Realtime Database (장애물 데이터)

Cloud Firestore (관리자 FCM 토큰)

알림: Firebase Cloud Messaging (FCM)

위치: Google Play Services Location (FusedLocationProviderClient)

위치 쿼리: davidmoten:geo (GeoHash 유틸리티)

🗺️ 주요 기능
지역(기관) 선택:

MainActivity에서 3단계 스피너(시/도, 시/구/군, 동)를 통해 관리 담당 지역을 선택합니다.

선택된 경로는 SharedPreferences에 저장되어 앱 재시작 시 유지됩니다.

장애물 데이터 연동:

선택된 지역 경로(organizations/...)를 기반으로 Firebase Realtime DB에서 해당 지역의 geohash 값을 불러옵니다.

카카오맵 모니터링:

불러온 geohash를 이용해 obstacles/{geohash} 경로의 모든 장애물 데이터를 MapActivity의 카카오맵 위에 마커로 표시합니다.

장애물 상세 정보 확인:

지도 위의 마커(Label)를 클릭하면, Base64로 인코딩된 장애물 이미지를 디코딩하여 다이얼로그로 보여줍니다.

실시간 푸시 알림 수신:

MyFirebaseMessageService를 통해 FCM 푸시 알림을 수신합니다.

새로운 장애물 신고가 발생했을 때, 관리자에게 즉각적인 알림(Heads-up Notification)을 보냅니다.

관리자 토큰 등록 (타겟 알림):

관리자가 지역을 설정하면, 해당 지역 geohash를 이름으로 하는 Firestore Collection에 관리자의 FCM 토큰과 기기 ID를 저장합니다.

이를 통해 서버는 특정 지역(geohash)에 신고가 들어왔을 때, 해당 Firestore Collection에 등록된 관리자들에게만 정확하게 푸시 알림을 보낼 수 있습니다.

⚙️ 아키텍처 및 동작 원리
지역 설정 (MainActivity):

관리자가 앱 실행 후 '기관 설정'에서 "서울특별시 / 강남구 / 역삼동"을 선택합니다.

앱은 Firebase Realtime DB의 organizations/서울특별시/강남구/역삼동에서 geohash 값 (예: "9q9j6j")을 조회합니다.

토큰 등록 (MainActivity):

조회한 geohash 값("9q9j6j")을 기반으로 Cloud Firestore의 9q9j6j 컬렉션에 이 관리자의 기기 ID와 FCM 토큰을 저장합니다. (타겟 푸시 알림을 위함)

이 작업은 PREF_TOKEN_SAVED 플래그를 통해 한 번만 수행됩니다.

지도 조회 (btn_map_tap):

관리자가 '지도 확인' 버튼을 누릅니다.

앱은 geohash 값을 MapActivity로 전달하며 액티비티를 전환합니다.

모니터링 (MapActivity):

MapActivity는 전달받은 geohash (예: "9q9j6j")를 사용하여 Realtime DB의 obstacles/9q9j6j 경로에 있는 모든 장애물 데이터를 불러옵니다.

불러온 데이터(위도, 경도)를 기반으로 카카오맵 위에 마커를 표시합니다.

마커 클릭 시, 해당 cctv_key의 imageData를 Base64 디코딩하여 이미지 다이얼로그를 띄웁니다.

실시간 알림 (MyFirebaseMessageService):

(서버에서) "9q9j6j" 지역에 새 장애물이 등록되면, 서버는 Firestore 9q9j6j 컬렉션의 모든 토큰을 조회하여 FCM을 발송합니다.

관리자 앱은 FCM을 수신하여 사용자에게 즉시 알림을 표시합니다.

🗄️ Firebase 데이터 구조
이 앱은 Realtime Database와 Cloud Firestore를 모두 사용합니다.

1. Realtime Database (장애물 데이터)
organizations: 기관별 geohash 정보를 저장합니다.

obstacles: geohash별 실제 장애물(CCTV)의 위치와 이미지 데이터를 저장합니다.

```JSON
{
  "organizations": {
    "서울특별시": {
      "강남구": {
        "역삼동": {
          "geohash": "9q9j6j" 
        }
      }
    }
  },
  "obstacles": {
    "9q9j6j": {
      "cctv_key_001": {
        "latitude": 37.5665,
        "longitude": 126.9780,
        "imageData": "aW1hZ2VfZGF0YV9pbl9iYXNlNjQ="
      },
      "cctv_key_002": {
        "latitude": 37.5666,
        "longitude": 126.9781,
        "imageData": "YW5vdGhlcl9iYXNlNjRfZGF0YQ=="
      }
    }
  }
}
```

2. Cloud Firestore (관리자 FCM 토큰)
geohash 값을 컬렉션 ID로 사용합니다.

관리자의 고유 기기 ID를 문서 ID로 사용합니다.

해당 기기의 FCM 토큰을 저장하여 타겟 알림 발송에 사용합니다.

```Bash
(Collection) 9q9j6j
  └ (Document) admin_device_id_001
      └ (Field) token: "fcm_token_for_device_001..."
  └ (Document) admin_device_id_002
      └ (Field) token: "fcm_token_for_device_002..."
```
