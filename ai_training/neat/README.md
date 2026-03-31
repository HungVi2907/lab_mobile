# NEAT Training Workspace (Flappy Bird)

Muc tieu cua thu muc nay la train AI bang Python, tach biet hoan toan voi app Android.
Logic simulator duoc map theo game loop hien tai trong app de winner JSON co the nap vao app va infer nhanh.

## Cau truc
- simulator.py: Mo phong physics, pipe recycle, collision, scoring
- train_neat.py: Train NEAT va luu winner genome
- export_winner_json.py: Chuyen winner genome sang flattened feed-forward JSON
- config-feedforward.txt: Cau hinh NEAT
- artifacts/: Luu output model

## Mapping logic app -> simulator
Nguon app:
- app/src/main/java/com/example/flappy_bird_clone/models/GameObjects.kt
- app/src/main/java/com/example/flappy_bird_clone/GameViewModel.kt

Da map cac diem chinh sau:
1. Gravity, flap velocity, pipe speed va profile difficulty giong GameTuning + profileFor().
2. Bird start: x = width * 0.28, y = height * 0.42.
3. Pipe init: 3 ong, first x = width * (1 + 0.28), spacing co jitter 18%.
4. Collision inset giong ViewModel:
   - birdInsetX = birdWidth * 0.18
   - birdInsetY = birdHeight * 0.14
   - pipeInsetX = pipeWidth * 0.08
5. Scoring: tang diem khi bird.x > pipe.x + pipeWidth va pipe chua scored.

## Inputs cho mang (flattened)
Thu tu input mac dinh:
1. bird_y_norm
2. bird_velocity_norm
3. next_pipe_dx_norm
4. gap_top_delta_norm
5. gap_bottom_delta_norm

Output:
- flap_score (neu > threshold thi flap)

## Setup nhanh
1. Tao virtual environment (khuyen nghi):
   Windows PowerShell:
   python -m venv .venv
   .\.venv\Scripts\Activate.ps1

2. Cai dependencies:
   pip install -r requirements.txt

3. Train nhanh (smoke):
   python train_neat.py --gens 2 --pop 10 --max-steps 900

4. Train day du:
   python train_neat.py --gens 50 --pop 50

5. Export winner JSON:
   python export_winner_json.py --winner artifacts/winner_genome.pkl --out artifacts/winner_network.json

6. Cham nhieu winner_network.json va chon model on dinh nhat (qua pipe dau):
   python evaluate_winner_networks.py --search-root . --glob "**/winner_network.json" --episodes 120 --top-k 5

7. Tu dong copy model tot nhat sang app assets:
   python evaluate_winner_networks.py --search-root . --glob "**/winner_network.json" --episodes 120 --copy-best

8. Chay pipeline train nang nhieu run + export + cham + copy best:
   python run_heavy_training_pipeline.py --runs 8 --gens 150 --pop 120 --episodes 3 --eval-episodes 160 --copy-best

## Output artifacts
- artifacts/winner_genome.pkl
- artifacts/winner_network.json
- artifacts/model_selection_report.json
- artifacts/model_selection_report.csv

## Luu y
- Thu muc nay khong sua logic app Android.
- Neu app thay doi physics, can cap nhat simulator.py de giam do lech domain.
- Script `evaluate_winner_networks.py` xep hang uu tien theo:
  1) first_pipe_pass_wilson_low95
  2) first_pipe_pass_rate
  3) mean_score
- Co the override threshold khi cham bang `--threshold-override 0.55` de so sanh cong bang giua cac model.
- Script `run_heavy_training_pipeline.py` tao candidates trong `artifacts/candidates/` va goi evaluator de chon model on dinh truoc khi copy vao app.
