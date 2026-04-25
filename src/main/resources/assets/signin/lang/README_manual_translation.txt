Manual localization format for this mod:

1. Use standard Minecraft language JSON files:
   - en_us.json
   - zh_cn.json
   - or any locale file like ja_jp.json, ru_ru.json

2. Keep keys unchanged. Only modify values.

3. Placeholder rules:
   - `%s` means dynamic parameter (number/text).
   - Do not delete or reorder `%s` unless you also update code.
   - Use `%%` if you need a literal percent character.

4. Encoding:
   - UTF-8
   - JSON object with key-value string pairs.

5. Example:
   "gui.signin.button.sign": "立即签到"
   "message.signin.clear.target_success": "已清空玩家 %s 的签到数据。"
