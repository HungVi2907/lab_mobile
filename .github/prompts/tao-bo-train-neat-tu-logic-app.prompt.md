---
name: "Tao Bo Train NEAT Tu Logic App"
description: "Quet logic game hien tai, tao thu muc Python train rieng, train NEAT va export winner network JSON"
argument-hint: "trainDir=<path> headless=<true|false> gens=<int> pop=<int> out=<json-path>"
agent: "agent"
---

Muc tieu:
Tao bo train NEAT bang Python tach biet voi app Android hien tai, dua tren logic game trong app va tham khao repo Tech With Tim.

Nguon tham khao bat buoc:
- Playlist: https://www.youtube.com/watch?v=MMxFDaIOHsE&list=PLzMcBGfZo4-lwGZWXz5Qgta_YNX3_vLS2
- Repo goc: https://github.com/techwithtim/NEAT-Flappy-Bird
- Logic tham chieu: https://raw.githubusercontent.com/techwithtim/NEAT-Flappy-Bird/master/flappy_bird.py
- Config tham chieu: https://raw.githubusercontent.com/techwithtim/NEAT-Flappy-Bird/master/config-feedforward.txt
- Tai lieu NEAT: https://neat-python.readthedocs.io/en/latest/config_file.html

Cach hieu argument:
- Neu co argument, phan tich key=value cho: trainDir, headless, gens, pop, out.
- Neu khong co argument, mac dinh:
  - trainDir = ai_training/neat
  - headless = true
  - gens = 50
  - pop = 50
   - out = ai_training/neat/artifacts/winner_network.json

Boi canh ma nguon can doc truoc khi sua:
- [GameObjects](../../app/src/main/java/com/example/flappy_bird_clone/models/GameObjects.kt)
- [GameViewModel](../../app/src/main/java/com/example/flappy_bird_clone/GameViewModel.kt)
- [GameScreen](../../app/src/main/java/com/example/flappy_bird_clone/GameScreen.kt)

Nguyen tac bat buoc:
1. Tach biet logic train khoi app: tao thu muc rieng, khong lam anh huong logic game hien tai.
2. Physics trong app la nguon su that, simulator Python phai bam sat.
3. Khong chen code train vao Android app.
4. Export winner network ra flattened feed-forward JSON de app co the nap va infer nhanh.
5. Uu tien thay doi nho, ro rang, co huong dan chay lai.

Quy trinh thuc hien:
1. Quet logic game hien tai, trich ra bang mapping physics + collision + scoring.
2. Tao thu muc train rieng (theo trainDir) va scaffold file toi thieu:
   - README.md
   - requirements.txt
   - config-feedforward.txt
   - simulator.py
   - train_neat.py
   - export_winner_json.py
   - artifacts/.gitkeep
3. Viet simulator.py de mo phong game theo logic app (input/state/update/collision).
4. Viet train_neat.py:
   - Khoi tao NEAT config.
   - Dinh nghia eval_genomes + fitness function.
   - Train theo gens/pop.
   - Luu winner genome ra file nhi phan (neu can).
5. Viet export_winner_json.py de xuat winner sang flattened feed-forward JSON:
   - metadata
   - input_order
   - output_order
   - evaluation_order
   - nodes_compact (bias, activation)
   - edges_compact (src_idx, dst_idx, weight)
6. Dam bao app khong bi sua logic ngoai y muon; neu can bo sung README goc thi chi them huong dan su dung.
7. Neu co the, chay thu lenh train ngan (smoke run) va lenh export JSON.

Yeu cau chat luong:
- Moi nhan dinh quan trong phai co file tham chieu cu the.
- Neu co diem mo ve collision/score, danh dau "can xac minh" va de xuat gia tri mac dinh an toan.
- Neu phat sinh loi tu file moi tao, uu tien sua ngay.

Format dau ra (bat buoc):

## 1. Mapping app logic -> Python simulator
- ...

## 2. Thu muc va file da tao
- ...

## 3. Lenh train va export
- ...

## 4. JSON schema xuat ra
- ...

## 5. Muc can xac minh
- ...

## 6. File tham chieu
- ...
