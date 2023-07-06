# 📁 TCP File Transfer App

This is a CLI application based on **_client-server_** model that allows its users to transfer files over the network.

---

## ✨ Description

The application has two mods:

**CLIENT** - uploads files to the specified server.

**SERVER** - handles connections with many **CLIENTS** and downloads files from them.

Uploaded files are stored on the file system of the **SERVER**.

**SERVER** provides information about current and average file download speed.

**CLIENT** provides information about the upload result.

---

## 🧰 Technologies

- Java 17
- picocli 4.6.3
- Log4j 2.19.0
- Lombok 1.18.24

---

## 🚀 Run

Run the following command from the project root directory:

- **SERVER** mode:

```

./gradle run --args="server --port=SERVER_PORT"

```

where:

- `SERVER_PORT` is a port which the **SERVER** will be listening on

Example:

```

./gradle run --args="server --port=12000"

```

- **CLIENT** mode:

```

./gradle run --args="client --hostname=SERVER_HOSTNAME --port=SERVER_PORT --path=FILE_PATH"

```

where:

- `SERVER_HOSTNAME` is the hostname of the **SERVER**
- `SERVER_PORT` is the port of the **SERVER**
- `FILE_PATH` is the path of the file which will be uploaded on the **SERVER**

Example:

```

./gradle run --args="client --hostname=server.com --port=12000 --path=/files/capybara.jpg"

```

---

## 💡 Usage

**SERVER** stores uploaded files in `{PROJECT_ROOT}/uploads` directory.

⚠️ Restrictions on transferred files:

- Size of the UTF-8 file path <= 4 KB
- File size <= 1 TB

Press `Ctrl+C` to stop the application.