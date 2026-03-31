---
name: "Tong Quan Ung Dung Tu Thu Muc"
description: "Duyet thu muc du an va trinh bay boi canh, kien truc, tinh nang cua ung dung"
argument-hint: "Pham vi quet (mac dinh: workspace), muc do chi tiet (ngan|vua|sau)"
agent: "agent"
---

Muc tieu:
Duyet toan bo thu muc duoc chi dinh, sau do trinh bay lai boi canh va tinh nang cua ung dung mot cach de hieu va co dan chung.

Cach hieu argument:
- Neu nguoi dung co truyen argument sau lenh prompt, dung argument do de xac dinh pham vi quet va muc do chi tiet.
- Neu khong co argument, mac dinh quet toan bo workspace hien tai va muc do chi tiet la "vua".

Ngon ngu dau ra:
- Su dung Tieng Viet cho toan bo phan trinh bay.

Quy trinh phan tich:
1. Uu tien doc cac tep va thu muc quan trong:
- app/src/main/AndroidManifest.xml
- app/src/main/java/** hoac app/src/main/kotlin/**
- app/src/main/res/**
- settings.gradle.kts, build.gradle.kts, app/build.gradle.kts, gradle/libs.versions.toml
2. Xac dinh va tong hop:
- Bai toan/boi canh ma ung dung dang giai quyet
- Cau truc du an va cac thanh phan chinh
- Danh sach tinh nang theo man hinh hoac theo user flow
- Thu vien/chuyen de ky thuat dang chu y
- Muc do hoan thien va cac khoang trong con thieu
3. Neu thong tin chua du chac chan, danh dau "can xac minh" va neu ro file can doc them.

Format dau ra (bat buoc):

## 1. Boi canh ung dung
- ...

## 2. Kien truc va cau truc ma nguon
- ...

## 3. Danh sach tinh nang
- ...

## 4. User flow chinh
- ...

## 5. Phu thuoc va cong nghe noi bat
- ...

## 6. Rui ro, khoang trong, va muc can xac minh
- ...

Yeu cau chat luong:
- Moi nhan dinh quan trong phai co tham chieu toi file cu the.
- Uu tien thong tin co the hanh dong ngay (nhung gi nguoi moi can hieu de tiep tuc phat trien).
- Trinh bay gon, ro, uu tien bullet points.