# Phone access: LAN API and Windows Firewall

The desktop app runs an API server so the Android app can reach it over Wi-Fi. This page covers what you have to set up once on Windows.

## How the server listens

- It listens only on the PC's LAN address: the IPv4 address of the network adapter that has the default route (your Wi-Fi or Ethernet). It does not listen on all interfaces. It also never listens on Hyper-V, WSL, Docker or VPN adapters, unless you pick one of them in Settings.
- The default port is **47821**. You can change it in Settings.
- The app checks the address every 45 seconds. If it changes (another Wi-Fi network, or a new DHCP lease), the server restarts on the new address and the pairing QR code changes. Pair the phone again after that.
- `GET /health` needs no token. Every `/v1/...` call needs `Authorization: Bearer <pairing token>`. "Re-pair" in the app replaces the token, and every phone has to pair again.

## Windows Firewall

1. **Make the Wi-Fi profile Private.** Open Settings > Network & internet > Wi-Fi > (your network) > Network profile type, and choose **Private network**. The rule below only applies to Private networks, so on a Public network (cafés, hotels) the phone cannot connect. That is intended.
2. **Add the inbound rule** in an elevated PowerShell:

   ```powershell
   New-NetFirewallRule -DisplayName JobFinder -Direction Inbound -Protocol TCP -LocalPort 47821 -Profile Private -RemoteAddress LocalSubnet -Action Allow
   ```

   If you changed the port, use that port instead of 47821. `-RemoteAddress LocalSubnet` lets in only devices on your own network.
3. **Do not press Cancel on the "allow java.exe" prompt.** When the server first starts, Windows may ask whether to allow Java on networks. Pressing Cancel creates a **Block** rule for that program, and Windows applies Block rules before Allow rules, so the rule above stops working. If that happened, delete the block rule:

   ```powershell
   Get-NetFirewallRule -Direction Inbound | Where-Object { $_.DisplayName -like '*java*' -and $_.Action -eq 'Block' } | Remove-NetFirewallRule
   ```

4. **Development and the installed app are different programs.** `./gradlew :desktopCMP:run` runs the JDK's `java.exe`, and the MSI install runs `JobFinder.exe`. A program-specific rule or a Windows prompt answer applies to one of them only. The port rule above covers both.

## Checking it

From `CMP/`, start the API server without a window:

```bash
./gradlew :desktopCMP:run --args="serve 600"
```

It prints the address and the pairing token. From the phone's browser, or from another computer on the same Wi-Fi:

```bash
curl http://<address>:47821/health
curl -H "Authorization: Bearer <token>" "http://<address>:47821/v1/jobs?status=new&limit=5"
```

`/health` answers `{"status":"ok",...}`. A call without the token answers 401 with `"code":"unauthorized"`.
