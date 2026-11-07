# Store Drink

Ứng dụng quản lý cửa hàng đồ uống dành cho bài tập Java. Ứng dụng có giao
diện desktop bằng **Java Swing**, gọi REST API cục bộ bằng HTTP Client của JDK
và sử dụng **JSON Server** để lưu dữ liệu.

> Đây là ứng dụng chạy cục bộ phục vụ học tập và thử nghiệm. Dữ liệu tài khoản
> trong file JSON là dữ liệu mẫu, không nên dùng nguyên trạng trong môi trường
> thật.

## Mục lục

- [Tổng quan](#tổng-quan)
- [Chức năng](#chức-năng)
- [Công nghệ và yêu cầu](#công-nghệ-và-yêu-cầu)
- [Cài đặt](#cài-đặt)
- [Chạy dự án](#chạy-dự-án)
- [Kiểm thử](#kiểm-thử)
- [Hướng dẫn sử dụng](#hướng-dẫn-sử-dụng)
- [Dữ liệu và API](#dữ-liệu-và-api)
- [Cấu trúc mã nguồn](#cấu-trúc-mã-nguồn)
- [Xử lý sự cố](#xử-lý-sự-cố)

## Tổng quan

Khi khởi động, ứng dụng mở màn hình đăng nhập. Sau khi xác thực, hệ thống
chuyển người dùng tới giao diện phù hợp với `role`:

```text
Main
└── LoginFrame
    ├── MainView (ADMIN / STAFF / CUSTOMER)
    └── GuestOrderFrame (khách đặt món tại bàn, không cần đăng nhập)
```

Ứng dụng Java kết nối cố định tới:

```text
http://127.0.0.1:3000
```

Địa chỉ này được khai báo trong
[`ApiClient.java`](src/main/java/utils/ApiClient.java). JSON Server phải được
khởi động trước khi mở các màn hình cần đọc hoặc ghi dữ liệu.

## Chức năng

### Đăng nhập và phân quyền

- Đăng nhập bằng username và password.
- Tài khoản không hoạt động không được sử dụng.
- Giao diện được hiển thị theo `role`: `ADMIN`, `STAFF` hoặc `CUSTOMER`.
- Có thể đăng xuất để quay lại màn hình đăng nhập.

### ADMIN

Admin có quyền quản trị toàn bộ cửa hàng:

- Xem dashboard và thống kê vận hành.
- Thêm, sửa, bật/tắt bán đồ uống.
- Quản lý danh mục đồ uống.
- Thêm, sửa và đổi trạng thái tài khoản nhân viên.
- Thêm, sửa và bật/tắt bàn.
- Thêm, sửa, xóa khách hàng.
- Xem và xử lý đơn hàng.

### STAFF

Nhân viên có thể:

- Xem dashboard thống kê vận hành.
- Xem danh sách đơn hàng.
- Duyệt đơn đang chờ.
- Hủy đơn và nhập lý do hủy.
- Hoàn tất thanh toán bằng tiền mặt hoặc chuyển khoản.
- Tạo đơn mang về tại quầy.
- Làm mới danh sách khi có dữ liệu mới.

### CUSTOMER

Tài khoản khách hàng có thể:

- Xem danh sách đồ uống đang bán.
- Thêm hoặc xóa món trong giỏ hàng.
- Điều chỉnh số lượng món.
- Xem tổng tiền.
- Gửi order ở trạng thái chờ duyệt.

File dữ liệu mẫu hiện có tài khoản `ADMIN` và `STAFF`. Muốn thử giao diện
`CUSTOMER`, cần thêm một user có `role: "CUSTOMER"` vào
[`database/db.json`](database/db.json).

### Đặt món tại bàn không cần đăng nhập

Từ màn hình đăng nhập, chọn **Khách đặt món tại bàn**:
1. Nhập mã số bàn hoặc mã QR của bàn.
2. Chọn đồ uống và số lượng.
3. Nhập tên khách hàng.
4. Có thể nhập thêm số điện thoại.
5. Nhấn **Gửi order**.

Đơn được tạo với:

```text
orderType      = DINE_IN
status         = PENDING
paymentMethod  = UNPAID
tableId        = mã bàn đã chọn
```

Chỉ những bàn đang hoạt động mới nhận được order. Nhân viên sẽ tiếp nhận,
kiểm tra và duyệt đơn.

## Công nghệ và yêu cầu

| Thành phần | Phiên bản / vai trò |
|---|---|
| Java | JDK 25 trở lên |
| Maven | 3.9 trở lên |
| Java Swing | Giao diện desktop |
| JSON Server | REST API giả lập |
| Gson | Chuyển đổi JSON và Java object |
| HTTP Client | Gọi API từ ứng dụng Java |

Phiên bản Java được cấu hình tại
[`pom.xml`](pom.xml) bằng thuộc tính `maven.compiler.release`.

Kiểm tra môi trường:

```bash
java -version
javac -version
mvn -version
node -v
npm -v
```

## Cài đặt

### 1. Cài JDK, Maven và Node.js

Cài JDK 25, Maven và Node.js nếu máy chưa có. Sau khi cài, mở terminal mới
để các lệnh trên có trong `PATH`.

### 2. Cài dependency Node.js

Mở terminal tại thư mục gốc `StoreDrink`:

```bash
npm install
```

Các package được khai báo trong [`package.json`](package.json):

- `json-server@0.17.4`
- `json-server-auth`

Nếu chưa muốn dùng `package.json`, có thể cài trực tiếp:

```bash
npm install json-server@0.17.4 json-server-auth
```

### 3. Tải dependency Java

Maven sẽ tự tải Gson khi biên dịch:

```bash
mvn dependency:resolve
```

## Chạy dự án

### Bước 1: Sao lưu dữ liệu (khuyến nghị)

Các thao tác thêm, sửa, xóa sẽ ghi trực tiếp vào
[`database/db.json`](database/db.json). Hãy sao lưu file trước khi thử
nghiệm:

```text
database/db.json -> database/db.json.backup
```

### Bước 2: Khởi động JSON Server

Tại thư mục gốc dự án, mở terminal thứ nhất:

```bash
npx json-server --watch database/db.json --host 127.0.0.1 --port 3000
```

Nếu chạy thành công, JSON Server sẽ lắng nghe tại:

```text
http://127.0.0.1:3000
```

Kiểm tra nhanh bằng trình duyệt hoặc curl:

```text
http://127.0.0.1:3000/drinks
http://127.0.0.1:3000/tables
```

### Bước 3: Biên dịch Java

Mở terminal thứ hai tại thư mục gốc:

```bash
mvn clean compile
```

Có thể đóng gói project bằng:

```bash
mvn package
```

## Kiểm thử

Chạy toàn bộ kiểm thử tự động bằng Maven:

```bash
mvn test
```

### Bước 4: Chạy `Main`

#### Chạy bằng IDE

Đây là cách đơn giản nhất:

1. Mở thư mục dự án bằng IntelliJ IDEA, Eclipse hoặc VS Code có Java
   Extension Pack.
2. Chờ Maven tải dependency.
3. Chạy file [`Main.java`](src/main/java/Main.java).

#### Chạy bằng Maven
Project hiện khai báo dependency và compiler trong Maven, nhưng không khai
bao plugin `exec-maven-plugin`. Vì vậy, nếu lệnh sau không được Maven nhận,
hãy chạy `Main.java` trực tiếp từ IDE:

```bash
mvn exec:java -Dexec.mainClass=Main
```

Khi chương trình mở, đăng nhập bằng một tài khoản mẫu hoặc chọn đặt món tại
bàn.

## Tài khoản mẫu

Các tài khoản hiện có trong [`database/db.json`](database/db.json):

| Username | Password | Role |
|---|---|---|
| `admin` | `123456` | `ADMIN` |
| `admin2` | `122223456` | `ADMIN` |
| `staff01` | `123456` | `STAFF` |
| `staff02` | `123456` | `STAFF` |
| `staff03` | `123456` | `STAFF` |
| `staff04` | `123456` | `STAFF` |
| `staff05` | `123456` | `STAFF` |

## Hướng dẫn sử dụng

### Quy trình xử lý đơn tại bàn

```text
Khách quét QR
    ↓
Chọn món và gửi order
    ↓
PENDING
    ↓ nhân viên duyệt
CONFIRMED
    ↓ thanh toán
COMPLETED
```

Đơn `PENDING` quá 30 phút chưa được duyệt có thể bị hệ thống tự động hủy.
Đơn `PENDING` hoặc `CONFIRMED` có thể được nhân viên hủy nếu nhập lý do
hợp lệ.

### Quy trình tạo đơn mang về

1. Đăng nhập bằng tài khoản `STAFF`.
2. Chọn **Tạo đơn tại quầy**.
3. Chọn đồ uống và số lượng.
4. Nhập tên khách hàng; số điện thoại là tùy chọn.
5. Lưu order.
6. Nhân viên duyệt và hoàn tất thanh toán.

Đơn mang về có:

```text
orderType = TAKEAWAY
tableId   = null
```

### Thanh toán

Chỉ đơn ở trạng thái `CONFIRMED` mới được hoàn tất thanh toán. Hai phương thức
được hỗ trợ:

```text
CASH      - Tiền mặt
TRANSFER  - Chuyển khoản
```

Sau khi thanh toán thành công, trạng thái đơn chuyển thành `COMPLETED`.

### Quản lý tồn kho

Khi đơn được duyệt, số lượng đồ uống trong order được đối chiếu với tồn kho.
Hệ thống lưu thông tin số lượng đã giữ trong `reservedStock`, đồng thời theo
dõi:

```text
stockDeducted - đã trừ kho hay chưa
stockRestored - đã hoàn kho hay chưa
```

Không nên chỉnh sửa trực tiếp các trường này trong JSON nếu chưa hiểu rõ
trạng thái đơn, vì có thể làm sai số lượng tồn kho.

## Dữ liệu và API

### Các resource

[`database/db.json`](database/db.json) hiện có các resource:

```text
/users
/categories
/tables
/drinks
/orders
/orderDetails
/customers
```

### Quan hệ dữ liệu

```text
Category 1 ───── N Drink
User 1 ───────── N Order
StoreTable 1 ─── N Order
Order 1 ──────── N OrderDetail
Drink 1 ──────── N OrderDetail
Customer 1 ───── N Order (khi order có liên kết khách hàng)
```

### Các trường chính của order

| Trường | Mô tả |
|---|---|
| `id` | Mã đơn |
| `tableId` | Mã bàn; `null` với đơn mang về |
| `customerName` | Tên khách |
| `customerPhone` | Số điện thoại, có thể bỏ trống |
| `orderType` | `DINE_IN` hoặc `TAKEAWAY` |
| `total` | Tổng tiền |
| `paymentMethod` | `UNPAID`, `CASH` hoặc `TRANSFER` |
| `status` | Trạng thái xử lý đơn |
| `createdAt` | Thời điểm tạo |
| `cancelReason` | Lý do hủy, nếu có |

### API thường dùng

```http
GET    /users
GET    /users?username=admin&password=123456
PUT    /users/:id
POST   /users

GET    /categories
POST   /categories
PUT    /categories/:id

GET    /tables
POST   /tables
PUT    /tables/:id

GET    /drinks
POST   /drinks
PUT    /drinks/:id

GET    /customers
POST   /customers
PUT    /customers/:id
DELETE /customers/:id

GET    /orders
POST   /orders
PUT    /orders/:id

GET    /orderDetails
GET    /orderDetails?orderId=:id
POST   /orderDetails
PUT    /orderDetails/:id
DELETE /orderDetails/:id
```

Ví dụ kiểm tra dữ liệu bằng curl:

```bash
curl http://127.0.0.1:3000/drinks
curl http://127.0.0.1:3000/orders
curl "http://127.0.0.1:3000/orderDetails?orderId=1"
```

## Cấu trúc mã nguồn

```text
StoreDrink/
├── database/
│   └── db.json                 # Dữ liệu JSON Server
├── src/main/java/
│   ├── Main.java               # Điểm bắt đầu ứng dụng
│   ├── model/                  # Java model
│   │   ├── Category.java
│   │   ├── Customer.java
│   │   ├── Drink.java
│   │   ├── Order.java
│   │   ├── OrderDetail.java
│   │   ├── StoreTable.java
│   │   └── User.java
│   ├── service/                # Nghiệp vụ và CRUD
│   │   ├── CategoryService.java
│   │   ├── CustomerService.java
│   │   ├── DashboardReportService.java
│   │   ├── DrinkService.java
│   │   ├── OrderService.java
│   │   ├── TableService.java
│   │   └── UserService.java
│   ├── utils/
│   │   ├── ApiClient.java      # HTTP GET/POST/PUT/DELETE
│   │   └── DataChangeNotifier.java
│   └── view/
│       ├── admin/              # Nhân viên, bàn, khách hàng, danh mục
│       ├── auth/               # Đăng nhập và khách đặt món
│       ├── customer/           # Đồ uống và giỏ hàng
│       ├── layout/             # MainView, theme, thông báo
│       └── staff/              # Dashboard, đồ uống, order
├── package.json                # Dependency Node.js
├── pom.xml                    # Dependency và build Java
└── README.md
```

## Xử lý sự cố

### API không kết nối được

- Kiểm tra terminal chạy JSON Server.
- Kiểm tra đúng file `database/db.json`.
- Kiểm tra cổng `3000` không bị chương trình khác sử dụng.
- Mở `http://127.0.0.1:3000/drinks` để xác nhận API phản hồi.
### Nhập mã bàn nhưng bị báo không tồn tại

- Mã nhập phải là `id` của bàn hoặc giá trị `qrCode`.
- Bàn phải có trạng thái `ACTIVE` hoặc `Đang hoạt động`.
- Kiểm tra dữ liệu tại `http://127.0.0.1:3000/tables`.

### Không đăng nhập được

- Kiểm tra username, password trong resource `/users`.
- Kiểm tra `active` của tài khoản là `true`.
- Đảm bảo JSON Server đang chạy trước khi đăng nhập.

### Maven báo lỗi phiên bản Java

Kiểm tra:

```bash
java -version
mvn -version
```

Cả Maven và IDE phải đang sử dụng JDK 25 hoặc mới hơn. Sau đó chạy lại:

```bash
mvn clean compile
```

### Dữ liệu bị thay đổi ngoài ý muốn

Dừng JSON Server, khôi phục bản sao của
[`database/db.json`](database/db.json), sau đó khởi động lại server. Không
đồng thời mở nhiều JSON Server cùng ghi vào một file dữ liệu.

## Mục tiêu học tập

Project minh họa các nội dung:

- Lập trình hướng đối tượng với Java.
- Xây dựng giao diện desktop bằng Swing.
- Tách model, service, view và utility.
- Gọi REST API bằng HTTP Client.
- Chuyển đổi JSON bằng Gson.
- Thực hiện CRUD.
- Phân quyền người dùng.
- Xử lý vòng đời order và thanh toán.
- Đồng bộ dữ liệu giữa nhiều màn hình Swing.
