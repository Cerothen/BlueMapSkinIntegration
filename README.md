# BlueMapSkinIntegration
This project is a generic Minecraft skins integration provider, there is only one configuration option that lets you search many sources for skins and provide the first usable back to bluemap. Currently supported integrations are:

 - Directory (relative or absolute)
 - Web Request (define a URL)
 - SkinsRestorer (API V15)
 - Mojang (Online / Offline)

## Configuration
```yaml
# Indicate providers that you would like to support
# providers should be in order of preference, first
# provider to return a valid skin will be utilized.
# Valid values are:
# skinsrestorer - Pull skin from skinsrestorerAPI V15
# mojang - Pull skin from official Mojang (Java)
# http(s)://<url> - URL to webservice, see substitutions
# dir:<directory> - DIR to folder, see substitutions
# relative to plugin folder if not leading "/"
# Supported Substitutions:
# {UUID} UUID of the player
# {UUID-} UUID of the player without "-"
# {UUID_} UUID of the player using "_" instead of "-"
# {USERNAME} Bukkit username as provided
# {USERNAME_UC} Bukkit username (UPPERCASE)
# {USERNAME_LC} Bukkit username (LOWERCASE)
providers:
- skinsrestorer
- mojang
# - "https://minotar.net/skin/{UUID}"
# - "dir:skins/{UUID}.png"
```