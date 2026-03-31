# Flappy Bird Clone (Android Jetpack Compose)

## 1. Muc tieu bai lab
Du an demo game 2D kieu Flappy Bird tren Android voi Jetpack Compose.
Nguoi choi tap de flap, vuot ong nuoc de tinh diem, va choi lai khi game over.

## 2. Pham vi tinh nang hien tai
- Main menu + Start Game
- Vong lap gameplay theo frame
- Va cham voi tran, mat dat, ong tren/duoi
- Tinh diem khi vuot qua ong
- High score luu qua SharedPreferences
- Pause/Resume trong luc choi
- Sound ON/OFF va beep cue cho start/flap/score/hit
- Chon do kho: Easy / Normal / Hard
- Ground sprite co san (`ground_sprite.xml`) va fallback an toan trong UI neu thieu drawable

## 3. Cong nghe
- Kotlin + Android Gradle Plugin
- Jetpack Compose + Material3
- ViewModel + StateFlow
- Unit test voi JUnit4

## 4. Tieu chi nghiem thu de xuat
1. App mo vao MainMenu, nhan Start vao WaitingToStart.
2. Tap de bat dau choi va flap.
3. Score tang khi vuot qua ong.
4. Va cham se vao GameOver va tap de restart.
5. Pause/Resume hoat dong khi dang Playing.
6. Sound toggle hoat dong va khong crash.
7. Difficulty thay doi duoc gap/spacing va feel gameplay.
8. High score duoc giu lai sau khi dong/mo lai app.
9. Build debug thanh cong.
10. Unit tests gameplay pass.

## 5. Kiem thu nhanh
Chay test:

```powershell
./gradlew test
```

Build debug:

```powershell
./gradlew assembleDebug
```

## 6. Chinh sach backup
- `android:allowBackup` dat `false` trong AndroidManifest.
- `backup_rules.xml` va `data_extraction_rules.xml` de o trang thai khong backup.

## 7. Muc can xac minh voi nhom hoc phan
1. Co bat buoc phai co am thanh bang file asset thuc hay beep system la du?
2. Co yeu cau bat buoc cho high score persistence hay chi can score runtime?
3. Co can bat backup du lieu nguoi choi khi nop bai hay khong?
4. Do kho co can UI phuc tap hon (slider, custom profile) hay chi can 3 muc la du?

## 8. AI training (tach biet voi app Android)
Thu muc train duoc tao rieng tai ai_training/neat de tranh anh huong logic game hien tai.

Quy trinh:
1. Train bang Python trong ai_training/neat.
2. Luu winner genome ra artifacts/winner_genome.pkl.
3. Export sang flattened JSON artifacts/winner_network.json.
4. App Android se nap winner_network.json de infer, khong train trong app.

Lenh nhanh:

```powershell
cd ai_training/neat
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python train_neat.py --gens 50 --pop 50
python export_winner_json.py --winner artifacts/winner_genome.pkl --out artifacts/winner_network.json
```

## 9. Tich hop winner_network.json vao app
- Model duoc nap tu asset mac dinh: `app/src/main/assets/winner_network.json`.
- Khong train trong app, chi infer feed-forward (activation: tanh, identity).
- Neu file thieu/loi parse, app tu dong fallback ve `Mode: Manual` va hien ly do `AI unavailable` tren UI.

Cach dat model:
1. Tao thu muc assets neu chua co: `app/src/main/assets/`.
2. Copy file da export:
   - tu: `ai_training/neat/artifacts/winner_network.json`
   - sang: `app/src/main/assets/winner_network.json`
3. Build/chay lai app, bat nut `Mode: AI` de test auto flap.

## 10. Chinh nhanh AI bang BuildConfig (khong sua code)
App co parser doc argument tu `BuildConfig.AI_RUNTIME_ARGS` voi format:
- `threshold=<0..1>`
- `cooldown=<ms>`
- `startMode=manual|ai`

Truyen qua Gradle property `aiRuntimeArgs` khi build:

```powershell
./gradlew assembleDebug -PaiRuntimeArgs="threshold=0.62,cooldown=80,startMode=ai"
```

Parser chap nhan dau phan tach `,` hoac `;`, va key alias:
- `threshold` hoac `th`
- `cooldown`, `cooldownMs`, `cooldown_ms`
- `startMode` hoac `mode`

Gia tri khong hop le se duoc bo qua va fallback ve default an toan.
