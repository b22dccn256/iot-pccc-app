# IoT PCCC App (Ứng Dụng Giám Sát Phòng Cháy Chữa Cháy)

Đây là một ứng dụng Android thuộc hệ thống IoT cảnh báo cháy nổ, được phát triển để theo dõi và cảnh báo các nguy cơ cháy dựa trên dữ liệu cảm biến (Nhiệt độ, Khí gas) và hình ảnh từ camera theo thời gian thực.

## 🌟 Tính Năng Chính

*   **Kết nối Server linh hoạt**: Cho phép người dùng chủ động cấu hình địa chỉ IP/Port của server điều khiển cục bộ.
*   **Giám sát theo thời gian thực**: Liên tục cập nhật các thông số về Nhiệt độ và Khí Gas từ cảm biến (sử dụng Retrofit kết nối tới API server).
*   **Đánh giá rủi ro thông minh**: Tự động phân tích các thông số để đưa ra 3 mức độ cảnh báo:
    *   **An Toàn** (Safe)
    *   **Nghi Ngờ Cháy** (Suspected)
    *   **Cảnh Báo Cháy** (Fire Confirmed)
*   **Báo động âm thanh**: Tự động phát âm thanh cảnh báo lớn (Alarm) khi hệ thống xác nhận trạng thái cháy.
*   **Tích hợp AI/YOLO Vision**: Hiển thị hình ảnh phân tích đám cháy (dựa trên YOLO) được trả về từ Server.
*   **Live Camera**: Hỗ trợ xem trực tiếp luồng video MJPEG từ ESP32-CAM ngay trên ứng dụng.
*   **Giao diện động**: Màu sắc và giao diện ứng dụng tự động thay đổi (nhấp nháy cảnh báo) dựa theo mức độ nguy hiểm của hiện trường.

## 🛠 Công Nghệ Sử Dụng

*   **Ngôn ngữ**: Kotlin
*   **Giao diện**: Jetpack Compose (Modern UI Toolkit)
*   **Networking**: Retrofit2 & Gson để gọi API và lấy dữ liệu JSON.
*   **Load Ảnh**: Coil Compose (Hỗ trợ load ảnh và luồng ảnh stream mượt mà).
*   **Kiến trúc Android**: ComponentActivity, Compose Navigation (State management).
*   **BaaS**: Google Firebase (Analytics, Firebase BOM, Realtime Database, Auth chuẩn bị sẵn).

## 📋 Yêu Cầu Hệ Thống

*   **Min SDK**: 24 (Android 7.0 Nougat)
*   **Target SDK**: 36
*   **JDK**: JavaVersion 11

## 🚀 Hướng Dẫn Cài Đặt

1.  **Clone dự án về máy:**
    ```bash
    git clone <repository_url>
    ```
2.  **Mở dự án:**
    Mở thư mục `IoT_PCCC_App` bằng Android Studio (phiên bản hỗ trợ Jetpack Compose mới nhất).
3.  **Build & Run:**
    *   Đồng bộ Gradle (`Sync Project with Gradle Files`).
    *   Kết nối thiết bị Android vật lý hoặc máy ảo (Emulator).
    *   Nhấn **Run** (Shift + F10) để cài đặt ứng dụng.

## 📱 Hướng Dẫn Sử Dụng

1.  **Màn hình cấu hình:** Khi mở ứng dụng lần đầu, bạn cần nhập địa chỉ IP và Port của Server (Ví dụ: `192.168.1.16:8000`).
2.  **Màn hình giám sát:** 
    *   Ứng dụng sẽ tự động tải dữ liệu cảm biến mỗi 0.5 giây.
    *   Bạn có thể xem các chỉ số T° (Nhiệt độ) / Gas trên màn hình.
    *   Nếu phát hiện nguy hiểm, ứng dụng sẽ hiện đỏ và phát âm thanh chuông báo động.
3.  **Xem Camera:** Xem trực tiếp hình ảnh stream từ ESP32-CAM hoặc các luồng phân tích nhận diện đám cháy được server gửi về.
4.  **Thay đổi Server IP:** Bấm vào biểu tượng cài đặt (bút chì) ở góc phải trên cùng để đổi lại địa chỉ IP server nếu cần.

## 📂 Cấu Trúc Thư Mục & Tài Liệu Bổ Sung

*   **`Code/`**: Thư mục chứa toàn bộ mã nguồn của ứng dụng Android.
*   **`Report/`**: Thư mục chứa các tài liệu báo cáo, file phân tích và thuyết trình của dự án.
    *   📄 [Report_Project.pdf](Report/Report_Project.pdf): Báo cáo chi tiết về dự án.

---
*Dự án IoT kết hợp giữa vi điều khiển (ESP/Arduino), Server AI (YOLO) và Ứng dụng Android App.*
