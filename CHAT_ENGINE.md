# Android Chat Engine — thành viên 3

## Chức năng

- `RecentChatsFragment`: danh sách hội thoại theo tin mới nhất, avatar, tên, preview theo loại tin, thời gian, số chưa đọc và tổng badge trên dock.
- Nhắn tin 1-1 từ danh bạ; tạo nhóm có tên với từ 2 đến 100 bạn, người tạo được thêm làm admin. Backend tạo nhóm trong transaction.
- `ChatViewModel` giữ tin nhắn, trạng thái gửi và bản nháp qua configuration change. `SavedStateHandle` khôi phục bản nháp khi hệ thống tạo lại màn hình.
- SQLite (`ChatStore`) lưu lịch sử và danh sách hội thoại riêng theo tài khoản. Mở chat đọc cache trước, sau đó gộp kết quả mạng theo ID. Không xóa tin realtime khi API lịch sử trả muộn.
- Socket.IO xác thực bằng JWT; server gửi tin đã lưu tới room riêng của mỗi thành viên. Kết nối lại sẽ lấy bù lịch sử. Không còn client tự broadcast nội dung tin.
- Gửi ảnh/video/tài liệu qua Android document picker; sao chép tệp trên luồng nền, giới hạn 50 MB và thanh tiến trình upload. Bubble file có icon, tên và dung lượng. Video phát trong dialog; audio có nút phát/dừng; tài liệu mở bằng URL.
- Giữ micro để ghi AAC, thả để gửi; giới hạn 2 phút. Khi bị ngắt/rời màn hình, bản ghi đang làm bị hủy và giải phóng recorder. Triển khai theo [MediaRecorder overview](https://developer.android.com/media/platform/mediarecorder).
- Gửi lỗi giữ bản nháp/tệp trong ViewModel và có nút Thử lại. Retry dùng cùng UUID, backend có unique key để không tạo bản tin trùng nếu phản hồi lần trước bị mất.
- Trạng thái đã đọc tăng theo ID cuối đang hiển thị, chỉ gửi acknowledgement khi màn hình chat đang ở foreground.

## Cập nhật database và chạy

Database mới: dùng `backend/database/schema.sql` như hướng dẫn cũ.

**Database đã tồn tại phải chạy migration một lần trước khi dùng server mới:**

```sql
SOURCE backend/database/migrations/001_chat_read_state.sql;
```

Lệnh `SOURCE` chạy trong MySQL client từ thư mục gốc repository; có thể mở file migration bằng MySQL Workbench và chạy trên database của dự án. Migration thêm `last_read_message_id`, `client_message_id` và unique key cho retry. Không chạy lại migration nếu các cột đã tồn tại.

```powershell
cd backend
npm ci
npm start
```

Android cần JDK 17, Android SDK 34 và backend đang chạy. Địa chỉ emulator mặc định là `http://10.0.2.2:5000/api/` trong `ApiClient.java`; điện thoại thật cần đổi sang IP LAN của máy chạy server. Cài cả backend và APK mới cùng nhau vì cơ chế Socket.IO đã đổi sang JWT/account room.

Trong phiên triển khai này, JDK 17 được tải vào `.tools/jdk17/jdk-17.0.21.0.2+1` (được gitignore). Trên máy hiện tại có thể đặt biến môi trường sau khi vào thư mục `android`:

```powershell
$env:JAVA_HOME = (Resolve-Path '..\.tools\jdk17\jdk-17.0.21.0.2+1').Path
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Phạm vi offline

Đã lưu nội dung/metadata tin nhắn và hội thoại để đọc sau khi mất mạng hoặc mở lại app. Tệp media từ server vẫn cần mạng nếu chưa được image cache tải trước. Upload và gửi tin mới cần mạng; gửi lỗi có thể thử lại khi màn hình còn giữ ViewModel. Chưa có hàng đợi gửi nền bền vững qua lần đóng app; tệp đang upload không được khôi phục nếu process bị hệ thống hủy.

## Kiểm thử

Đã xác nhận ngày 16/11/2026: build debug thành công; 19 Android unit test (gồm 9 ChatViewModelTest và 5 MessageFormatTest) và 9 backend test đều qua. `git diff --check` và kiểm tra cú pháp XML cũng qua. Chưa có thiết bị/máy ảo kết nối; chưa chạy nghiệm thu UI/micro/video hoặc integration test với MySQL thật. Migration chưa được áp dụng lên database người dùng.

- `MessageFormatTest`: UTC/offset/giờ local, ngày cũ/năm cũ, dữ liệu không hợp lệ và preview media.
- `ChatViewModelTest`: không tải lại khi gọi start nhiều lần, gộp history/realtime, chống trùng API/socket, bản nháp khi lỗi, sửa draft trong khi gửi, retry giữ UUID, tin mới có UUID khác, đọc chỉ khi foreground và giải phóng tài nguyên.
- `backend/test/chat.test.js`: dùng DB double để kiểm tra quyền gửi, broadcast sau lưu, file bắt buộc, rollback tạo nhóm, acknowledgement tăng dần và xác thực socket. Chạy `node --test` trong `backend`. Các test này không thay thế integration test với MySQL thật.

Các bước nghiệm thu trên ít nhất hai tài khoản:

1. Mở chat 1-1, gửi hai chiều; mỗi tin chỉ xuất hiện một lần. Để người nhận ở danh sách hội thoại và kiểm tra badge tăng; mở chat để badge về 0.
2. Tạo nhóm với hai bạn; dùng tài khoản thành viên mở nhóm và nhắn tin. Xác nhận cả ba bên nhận được tin và thấy tên người gửi.
3. Mở chat có lịch sử, tắt mạng, đóng/mở lại app và xem lịch sử đã lưu. Bật mạng, nhận các tin bị bỏ lỡ mà không trùng.
4. Nhập draft và xoay màn hình; kiểm tra tin nhắn/draft còn nguyên. Xoay trong lúc gửi và kiểm tra request không được gửi thêm.
5. Gửi ảnh, video, PDF, DOCX, ZIP; kiểm tra tiến trình, tên/dung lượng, trình phát video và mở tài liệu. Chọn file >50 MB phải bị từ chối.
6. Từ chối rồi cấp quyền micro; giữ/thả để gửi AAC, phát/dừng, thử thả rất nhanh và rời app khi đang ghi. Không crash hoặc tiếp tục giữ micro.
7. Ngắt mạng khi gửi, kiểm tra draft không mất; bật mạng và bấm Thử lại. Xác nhận server chỉ lưu một bản tin.
