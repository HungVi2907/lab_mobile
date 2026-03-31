---
name: "Khac Phuc Chat Luong Flappy Lab"
description: "Khac phuc checklist loi chat luong gameplay, tai nguyen, backup va tai lieu cho flappy_bird_clone"
argument-hint: "Uu tien hang muc: tests|assets|core|backup|docs|all (mac dinh: all)"
agent: "agent"
---

Muc tieu:
Khac phuc cac khoang trong chat luong da biet trong du an flappy_bird_clone, bao gom ca thay doi code, tai lieu, va cac muc can xac minh yeu cau lab.

Checklist mac dinh:
1. Gameplay tests: thay test mau bang unit tests co y nghia cho logic choi (uu tien unit tests).
2. Ground sprite: xu ly thieu ground_sprite ro rang, tranh fallback gay sai giao dien.
3. Feature scope: mac dinh implement day du high score, pause, am thanh, do kho; neu bi chan boi yeu cau lab thi danh dau can xac minh.
4. Backup rules: mac dinh vo hieu hoa backup/data extraction cho den khi co yeu cau luu du lieu ro rang.
5. README: tao tai lieu mo ta pham vi tinh nang va tieu chi nghiem thu.

Cach hieu argument:
- Neu co argument, chi uu tien cac nhom duoc chi dinh: tests, assets, core, backup, docs, all.
- Neu khong co argument, xu ly tat ca (all).

Rang buoc thuc thi:
- Su dung Tieng Viet cho toan bo bao cao.
- Uu tien sua truc tiep trong workspace thay vi chi de xuat.
- Voi muc can xac nhan tu giang vien/nhom hoc phan, danh dau "can xac minh" va dua 2 phuong an ro rang.
- Moi nhan dinh quan trong can co tham chieu file cu the.

Mac dinh khi thieu thong tin:
- Uu tien viet unit tests cho state transitions, scoring, va collision.
- Muc feature scope duoc trien khai day du tru khi co rang buoc ro rang tu de bai.
- Backup/data extraction dat ve trang thai toi gian (khong backup) de tranh luu du lieu ngoai y muon.

Quy trinh:
1. Doc nhanh cac file lien quan:
- app/src/main/java/**
- app/src/main/res/**
- app/src/test/**, app/src/androidTest/**
- app/src/main/res/xml/backup_rules.xml, app/src/main/res/xml/data_extraction_rules.xml
- README* (neu co)
2. Lap bang trang thai checklist (fixed/in-progress/can xac minh/blocked).
3. Ap dung cac thay doi co the quyet dinh ngay (unit tests, xu ly sprite fallback, implementation feature scope, README, cleanup rules).
4. Chay kiem tra phu hop neu co (test hoac build) va tong hop ket qua.
5. Neu con muc mo, liet ke cau hoi can gui nhom hoc phan.

Format dau ra (bat buoc):

## 1. Trang thai checklist
- [Gameplay tests] ...
- [Ground sprite] ...
- [Feature scope] ...
- [Backup rules] ...
- [README] ...

## 2. Thay doi da ap dung
- ...

## 3. Muc can xac minh voi nhom hoc phan
- ...

## 4. Ke hoach tiep theo
- ...

## 5. File tham chieu
- ...