# 🥤 Store Drink Management

> Hệ thống quản lý cửa hàng đồ uống được xây dựng bằng **Java Core + Java Swing + JSON Server**.
> npx json-server --watch database/db.json --host 0.0.0.0 --port 3000

## 📌 Giới thiệu

**Store Drink Management** là ứng dụng quản lý cửa hàng đồ uống.

Hệ thống hỗ trợ 3 loại tài khoản:

- **ADMIN** – Quản lý toàn bộ hệ thống
- **STAFF** – Nhân viên bán hàng và xử lý Order
- **CUSTOMER** – Khách hàng xem đồ uống và đặt Order

Dữ liệu được lưu trữ thông qua **JSON Server**, Java Swing đóng vai trò giao diện người dùng.

---

# 🚀 Công nghệ sử dụng

| Công nghệ   | Mục đích                 |
| ----------- | ------------------------ |
| Java 21     | Ngôn ngữ lập trình       |
| Java Swing  | Xây dựng giao diện       |
| JSON Server | Backend REST API giả lập |
| JSON        | Lưu trữ dữ liệu          |
| HTTP Client | Gọi API                  |
| OOP         | Thiết kế chương trình    |

---

# 👥 Phân quyền

## 👑 ADMIN

Admin có toàn quyền quản lý hệ thống.

- Dashboard
- Quản lý đồ uống
- Quản lý loại đồ uống
- Quản lý tài khoản
- Quản lý khách hàng
- Quản lý Order
- Quản lý thanh toán
- Thống kê doanh thu

## 👤 STAFF

Nhân viên sử dụng các chức năng liên quan đến bán hàng.

- Dashboard
- Xem đồ uống
- Quản lý khách hàng
- Tạo Order tại quầy
- Xem Order
- Thanh toán

## 🧑 CUSTOMER

Khách hàng có giao diện riêng.

- Trang chủ
- Xem danh sách đồ uống
- Tìm kiếm đồ uống
- Thêm đồ uống vào giỏ hàng
- Đặt Order
- Xem Order của mình
- Xem thông tin cá nhân

---

# 📋 Chức năng chính

## 🔐 Đăng nhập

Người dùng đăng nhập bằng:

- Username
- Password

Sau khi đăng nhập, hệ thống kiểm tra `role` để chuyển đến giao diện tương ứng.

```text
ADMIN
   ↓
Admin Dashboard

STAFF
   ↓
Staff Dashboard

CUSTOMER
   ↓
Customer Home
```

---

# 🥤 Quản lý đồ uống

Admin có thể:

- Xem danh sách đồ uống
- Thêm đồ uống
- Sửa đồ uống
- Xóa đồ uống
- Tìm kiếm đồ uống
- Quản lý số lượng
- Quản lý giá
- Quản lý trạng thái

Thông tin đồ uống:

```text
ID
Tên đồ uống
Loại đồ uống
Giá
Số lượng
Mô tả
Trạng thái
```

---

# 📁 Quản lý loại đồ uống

Admin có thể:

- Thêm loại
- Sửa loại
- Xóa loại
- Xem danh sách loại

Các loại mẫu:

```text
Cà phê
Trà
Nước ép
Sinh tố
Đá xay
```

---

# 🛒 Đặt hàng

Customer có thể:

1. Xem danh sách đồ uống
2. Chọn đồ uống
3. Thêm vào giỏ hàng
4. Thay đổi số lượng
5. Xem tổng tiền
6. Đặt Order
7. Theo dõi trạng thái Order

Ví dụ:

```text
Cà phê sữa × 1 = 25.000đ
Trà đào × 2    = 60.000đ
------------------------
Tổng           = 85.000đ
```

---

# 🧾 Order

Phân quyền vận hành:

```text
STORE DRINK
├── ADMIN
│   ├── Quản lý đồ uống, khách hàng và hệ thống
│   ├── Xem đơn hàng
│   └── Dashboard / thống kê
└── STAFF
    ├── Xem đơn
    ├── Duyệt đơn
    ├── Hủy đơn
    ├── Pha chế
    ├── Thanh toán
    └── Tạo đơn tại quầy
```

Giao diện ứng dụng được tách theo vai trò:

- `ADMIN`: Dashboard, Đồ uống, Danh mục, Nhân viên, Bàn, Đơn hàng và
  Thống kê.
- `STAFF`: Đơn hàng để duyệt/hủy/pha chế/thanh toán và Tạo đơn tại quầy.
- Khách hàng không đăng nhập; khách đặt món từ QR của bàn.

Khách hàng đặt món qua QR tại bàn theo luồng không cần đăng nhập được mô tả
ở phần thông tin khách hàng bên dưới.

Mỗi Order bao gồm:

```text
Order
│
├── Loại đơn (DINE_IN / TAKEAWAY)
├── Bàn (chỉ áp dụng cho DINE_IN)
├── Tên khách hàng
├── Số điện thoại (không bắt buộc)
├── Nhân viên tạo đơn
├── Ngày đặt
├── Tổng tiền
├── Phương thức thanh toán
├── Trạng thái
│
└── Order Details
      ├── Đồ uống
      ├── Số lượng
      ├── Đơn giá
      └── Thành tiền
```

Trạng thái Order:

```text
PENDING
CONFIRMED
COMPLETED
CANCELLED
```

Khách có thể đặt món sau khi quét QR của bàn. Đơn mới ở trạng thái
`PENDING`, sau đó nhân viên duyệt thành `CONFIRMED`; khi khách thanh toán,
đơn chuyển thành `COMPLETED`. Đơn `PENDING` (và tùy chính sách có thể cả
`CONFIRMED`) được phép chuyển thành `CANCELLED`.

Nhân viên có thể tạo đơn mang về từ màn hình `Đơn mang về`. Đơn mang về
không gắn bàn (`tableId = null`), bắt buộc có `customerName`, còn
`customerPhone` là tùy chọn. Đơn tại bàn dùng `orderType = DINE_IN` và
`tableId` tương ứng; đơn mang về dùng `orderType = TAKEAWAY`.

Khách hàng không cần tài khoản. Sau khi quét QR tại bàn, khách mở chức năng
`Khách đặt món tại bàn`, nhập mã bàn (mã được lấy từ QR), chọn đồ uống, nhập
`customerName` và tùy chọn `customerPhone`, sau đó bấm `Gửi order`. Hệ thống
lưu đơn với `status = PENDING`, `orderType = DINE_IN`, `tableId` của bàn và
`paymentMethod = UNPAID`; nhân viên sẽ xem và duyệt đơn.

---

# 💰 Thanh toán

Hệ thống hỗ trợ:

- Tiền mặt
- Chuyển khoản

Thông tin thanh toán:

```text
Mã thanh toán
Mã Order
Số tiền
Phương thức
Ngày thanh toán
Trạng thái
```

---

# 📊 Thống kê

Admin có thể xem:

- Tổng số Order
- Số Order đã thanh toán
- Số Order đang chờ
- Số Order đã hủy
- Tổng doanh thu

---

# 🗂️ Cấu trúc dữ liệu

JSON Server gồm các resource:

```text
users
categories
drinks
customers
orders
orderDetails
payments
```

Quan hệ:

```text
Category 1 ───── N Drink

User 1 ───────── N Order

Customer 1 ───── N Order

Order 1 ───────── N OrderDetail

Drink 1 ───────── N OrderDetail

Order 1 ───────── 1 Payment
```

---

# 📁 Cấu trúc Project

```text
StoreDrink/
│
├── src/
│   │
│   ├── model/
│   │   ├── User.java
│   │   ├── Category.java
│   │   ├── Drink.java
│   │   ├── Customer.java
│   │   ├── Order.java
│   │   ├── OrderDetail.java
│   │   └── Payment.java
│   │
│   ├── service/
│   │   ├── UserService.java
│   │   ├── CategoryService.java
│   │   ├── DrinkService.java
│   │   ├── CustomerService.java
│   │   ├── OrderService.java
│   │   └── PaymentService.java
│   │
│   ├── view/
│   │   ├── auth/
│   │   │   └── LoginFrame.java
│   │   ├── layout/
│   │   │   └── MainView.java
│   │   ├── staff/
│   │   │   ├── DashboardPanel.java
│   │   │   ├── DrinkPanel.java
│   │   │   └── OrderPanel.java
│   │   └── customer/
│   │       ├── CustomerPanel.java
│   │       ├── CustomerDrinkPanel.java
│   │       ├── CustomerShopPanel.java
│   │       ├── CustomerCart.java
│   │       └── CustomerCartPanel.java
│   │       └── MyOrderPanel.java
│   │
│   ├── utils/
│   │   ├── HttpClient.java
│   │   ├── JsonUtils.java
│   │   └── Session.java
│   │
│   └── Main.java
│
├── db.json
├── README.md
└── pom.xml
```

---

# ⚙️ Cài đặt JSON Server

## 1. Cài Node.js

Cài Node.js nếu máy chưa có.

Kiểm tra:

```bash
node -v
npm -v
```

## 2. Cài JSON Server

```bash
npm install -g json-server
```

## 3. Chạy JSON Server

Mở Terminal tại thư mục chứa `db.json`:

```bash
json-server --watch db.json --port 3000
```

API sẽ chạy tại:

```text
http://localhost:3000
```

---

# 🌐 Các API chính

## Users

```text
GET    /users
GET    /users/1
POST   /users
PUT    /users/1
DELETE /users/1
```

## Categories

```text
GET    /categories
POST   /categories
PUT    /categories/1
DELETE /categories/1
```

## Drinks

```text
GET    /drinks
GET    /drinks/1
POST   /drinks
PUT    /drinks/1
DELETE /drinks/1
```

## Customers

```text
GET    /customers
POST   /customers
PUT    /customers/1
DELETE /customers/1
```

## Orders

```text
GET    /orders
GET    /orders/1
POST   /orders
PUT    /orders/1
DELETE /orders/1
```

## Order Details

```text
GET    /orderDetails
POST   /orderDetails
PUT    /orderDetails/1
DELETE /orderDetails/1
```

## Payments

```text
GET    /payments
POST   /payments
PUT    /payments/1
DELETE /payments/1
```

---

# 🔑 Tài khoản mẫu

| Username   | Password | Role     |
| ---------- | -------- | -------- |
| admin      | 123456   | ADMIN    |
| staff01    | 123456   | STAFF    |
| staff02    | 123456   | STAFF    |
| customer01 | 123456   | CUSTOMER |
| customer02 | 123456   | CUSTOMER |
| customer03 | 123456   | CUSTOMER |

---

# ▶️ Chạy chương trình

### Bước 1: Chạy JSON Server

```bash
json-server --watch db.json --port 3000
```

### Bước 2: Kiểm tra API

Mở trình duyệt:

```text
http://localhost:3000/drinks
```

Nếu thấy danh sách đồ uống thì JSON Server đã chạy thành công.

### Bước 3: Chạy Java

Chạy:

```text
Main.java
```

### Bước 4: Đăng nhập

Ví dụ:

```text
Username: admin
Password: 123456
```

Hệ thống sẽ mở giao diện Admin.

---

# 🎯 Mục tiêu của Project

Project được xây dựng nhằm thực hành:

- Java Core
- Lập trình hướng đối tượng
- Java Swing
- Collections
- CRUD
- REST API
- JSON
- HTTP Request
- Phân quyền người dùng
- Quản lý Order
- Thanh toán
- Xử lý dữ liệu giữa Java và JSON Server

---

# 👨‍💻 Tác giả

**Store Drink Management**

Java Core Project
