---
name: "Them AI NEAT vao Flappy App"
description: "Tich hop model NEAT da train vao app Flappy Android (Compose + ViewModel), giu physics on dinh va them AI mode"
argument-hint: "model=<path> threshold=<0..1> cooldownMs=<int> mode=<manual|ai>"
agent: "agent"
---

Muc tieu:
Tich hop model NEAT da train vao app Flappy hien tai de suy luan tren thiet bi (on-device inference), khong goi API.

Nguon tham khao (bat buoc):
- Playlist: https://www.youtube.com/watch?v=MMxFDaIOHsE&list=PLzMcBGfZo4-lwGZWXz5Qgta_YNX3_vLS2
- Channel: Tech With Tim
- Repo: https://github.com/techwithtim/NEAT-Flappy-Bird
- Logic tham chieu: https://raw.githubusercontent.com/techwithtim/NEAT-Flappy-Bird/master/flappy_bird.py
- NEAT config tham chieu: https://raw.githubusercontent.com/techwithtim/NEAT-Flappy-Bird/master/config-feedforward.txt
- Tai lieu NEAT: https://neat-python.readthedocs.io/en/latest/config_file.html

Cach hieu argument:
- Neu co argument, phan tich cac cap key=value cho: model, threshold, cooldownMs, mode.
- Neu khong co argument, mac dinh:
  - model = app/src/main/assets/neat_winner.json
  - threshold = 0.5
  - cooldownMs = 100
  - mode = ai

Schema model mac dinh:
- Su dung winner genome JSON day du (nodes + connections + activation metadata), khong dung flattened JSON.

Boi canh ma nguon can doc truoc khi sua:
- [GameObjects](../../app/src/main/java/com/example/flappy_bird_clone/models/GameObjects.kt)
- [GameViewModel](../../app/src/main/java/com/example/flappy_bird_clone/GameViewModel.kt)
- [GameScreen](../../app/src/main/java/com/example/flappy_bird_clone/GameScreen.kt)

Nguyen tac bat buoc:
1. Giu physics dang dung trong app la nguon su that (khong doi gravity/flap/pipe speed neu khong duoc yeu cau).
2. Khong train trong app, chi nap model da train va infer.
3. Khong goi API cho moi frame game.
4. Bao toan choi tay: che do Manual van hoat dong binh thuong.
5. Uu tien thay doi nho, ro rang, de test.

Quy trinh thuc hien:
1. Them che do dieu khien Manual/AI vao state model trong GameObjects.
2. Tao bo danh gia mang NEAT feed-forward tu winner genome JSON day du (node, connection, weight, bias, activation).
3. Trong game loop update cua GameViewModel:
   - Rut trich input tu state moi frame (birdY, birdVelocityY, khoang cach den pipe tiep theo, khoang cach den tam gap, gapHeight, pipeSpeed neu co).
   - Normalize input ve mien on dinh.
   - Goi infer va flap neu output vuot threshold, co cooldown.
4. Them UI toggle bat/tat AI tren GameScreen.
5. Neu chua co file model JSON, tao mau winner genome schema + ghi chu cach thay model that.
6. Cap nhat README ngan gon cho luong train Python -> export JSON -> nap vao app.
7. Chay build/test lien quan neu co the; neu khong chay duoc thi neu ly do.

Yeu cau chat luong:
- Moi thay doi phai kem file tham chieu cu the.
- Neu gap diem mo, danh dau "can xac minh" va dua 1 phuong an mac dinh an toan.
- Neu phat sinh loi compile tu thay doi moi, uu tien sua ngay.

Format dau ra (bat buoc):

## 1. Ke hoach tich hop
- ...

## 2. Thay doi da ap dung
- ...

## 3. Cach nap model JSON va mapping input
- ...

## 4. Cach kiem tra nhanh trong app
- ...

## 5. Muc can xac minh
- ...

## 6. File tham chieu
- ...
