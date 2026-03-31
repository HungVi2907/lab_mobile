---
name: "Tich Hop Winner Network Vao ViewModel"
description: "Nap winner_network.json vao ViewModel, them AI mode va cho phep chim tu flap theo output NEAT"
argument-hint: "model=<path> threshold=<0..1> cooldownMs=<int> startMode=<manual|ai>"
agent: "agent"
---

Muc tieu:
Tich hop truc tiep winner_network.json da train vao app Android de bat che do AI choi tu dong.

Cach hieu argument:
- Neu co argument, phan tich key=value cho: model, threshold, cooldownMs, startMode.
- Neu khong co argument, mac dinh:
  - model = app/src/main/assets/winner_network.json
  - threshold = 0.5
  - cooldownMs = 100
  - startMode = manual

Boi canh ma nguon can doc truoc khi sua:
- [GameObjects](../../app/src/main/java/com/example/flappy_bird_clone/models/GameObjects.kt)
- [GameViewModel](../../app/src/main/java/com/example/flappy_bird_clone/GameViewModel.kt)
- [GameScreen](../../app/src/main/java/com/example/flappy_bird_clone/GameScreen.kt)
- [MainActivity](../../app/src/main/java/com/example/flappy_bird_clone/MainActivity.kt)

Schema model ky vong:
- Flattened feed-forward JSON tu export_winner_json.py voi cac truong:
  - metadata
  - input_order
  - output_order
  - input_node_indices
  - output_node_indices
  - evaluation_order
  - nodes_compact
  - edges_compact

Nguyen tac bat buoc:
1. Khong train trong app, chi infer.
2. Khong lam thay doi physics hien tai cua game.
3. Bao toan che do choi tay (manual) va co the bat/tat AI.
4. Neu model loi hoac thieu file, app fallback ve manual va khong crash.
5. Khong tu dong copy model vao assets; chi huong dan cach copy ro rang.
6. Uu tien thay doi nho, bat buoc co unit test cho evaluator va mode switch.

Quy trinh thuc hien:
1. Tao model classes + JSON loader cho winner_network.json.
2. Tao evaluator feed-forward trong app:
   - Ho tro activation toi thieu: tanh, identity.
   - Duyet evaluation_order, cong tong edge theo dst_idx.
   - Sinh output theo output_node_indices.
3. Bo sung trang thai dieu khien trong game state (manual/ai).
4. Trong update loop cua GameViewModel:
   - Rut trich input tu state moi frame va normalize.
   - Goi evaluator de lay flap_score.
   - Neu flap_score > threshold va dat cooldown thi flap.
5. Bo sung UI toggle mode trong GameScreen.
6. Neu model chua ton tai trong assets, tao huong dan copy tu ai_training/neat/artifacts/winner_network.json.
7. Them unit test cho evaluator va mode switch.
8. Chay build/test lien quan neu co the.

Yeu cau chat luong:
- Moi thay doi quan trong can co file tham chieu cu the.
- Neu gap diem mo, danh dau "can xac minh" va dua default an toan.
- Neu phat sinh compile error do thay doi moi, uu tien sua ngay.

Format dau ra (bat buoc):

## 1. Ke hoach tich hop
- ...

## 2. Thay doi da ap dung
- ...

## 3. Mapping input va logic flap
- ...

## 4. Cach dat winner_network.json
- ...

## 5. Muc can xac minh
- ...

## 6. File tham chieu
- ...
