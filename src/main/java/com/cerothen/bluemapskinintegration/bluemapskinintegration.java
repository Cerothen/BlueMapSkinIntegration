package com.cerothen.bluemapskinintegration;

// Annotations
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

// Core Java Misc
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;

// Java Utilities
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Base64;
import java.util.UUID;

// Core Bukkit
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

// technicjelle Tools
import com.technicjelle.MCUtils;

// BlueMapAPI
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.plugin.SkinProvider;

// SkinsRestorerAPI
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.VersionProvider;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.storage.PlayerStorage;
import net.skinsrestorer.api.property.SkinProperty;

import org.json.simple.JSONArray;
// JSON Support
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public final class bluemapskinintegration extends JavaPlugin {
	// Globals
	private final int bStatsID = 20396;
	private final Logger logger = getLogger();
	private Boolean serverOnlineMode = true;
	private JSONParser jsonParser;
	private SkinsRestorer skinsRestorerAPI;

	@Override
	public void onEnable() {
		// Initialization
		logger.info(this.getDescription().getName());
		logger.info("Version " + this.getDescription().getVersion());
		logger.info(this.getDescription().getWebsite());
		// Metrics ID
		new Metrics(this, bStatsID);
		// Components
		jsonParser = new JSONParser();
		// Bluemap (Required)
		BlueMapAPI.onEnable(blueMapOnEnableListener);
		// Create Skins Directory
		new File(this.getDataFolder().toString() + "/Skins").mkdirs();
		// SkinsRestorerAPI (Optional)
		try {
			// Retrieve the SkinsRestorer API for applying the skin
			skinsRestorerAPI = SkinsRestorerProvider.get();
			if (!VersionProvider.isCompatibleWith("15")) {
				logger.info("This plugin was made for SkinsRestorer v15, but " + VersionProvider.getVersionInfo() + " is installed. There may be errors!");
			}			
			System.out.println("SkinsRestorer support is enabled!");
		}
		catch(Exception e) {
			System.out.println("SkinsRestorer not found.");
		}
	}

	private final Consumer<BlueMapAPI> blueMapOnEnableListener = blueMapAPI -> {
		//Copy config.yml from jar to config folder
		try {
			MCUtils.copyPluginResourceToConfigDir(this, "config.yml", "config.yml", false);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to copy config.yml from jar to config folder!", e);
		}

		//Load config from disk
		reloadConfig();

		//Load config values into variables
		serverOnlineMode = this.getServer().getOnlineMode();
		List<String> providersList = getConfig().getStringList("providers");
		
		// Iterate over list values
		for (String provider : providersList) {
			// Validate Providers
			if (provider.equalsIgnoreCase("skinsrestorer")) logger.info("Enabled SkinsRestorer");
			else if (provider.equalsIgnoreCase("mojang")) logger.info("Enabled Mojang");
			else if (provider.matches("https?://.*")) logger.info("Enabled URL: " + provider);
			else if (provider.matches("dir:.*")) logger.info("Enabled DIR: " + provider);
			else { logger.info("Not supported: " + provider); }
		}

		// Establish Provider Function
		SkinProvider customSkinProvider;
		customSkinProvider = playerUUID -> {
			@Nullable String bukkitName = getServer().getOfflinePlayer(playerUUID).getName();
			@NotNull String username = bukkitName != null ? bukkitName : playerUUID.toString();
			Optional<BufferedImage> skinImage = Optional.empty();
			// Process Providers
			for (String provider : providersList) {
				// Process providers
				if (provider.equalsIgnoreCase("skinsrestorer")) skinImage = getSkinsRestorer(playerUUID, username);
				if (provider.equalsIgnoreCase("mojang")) skinImage = getMojang(playerUUID, username);
				if (provider.matches("https?://.*")) skinImage = getURL(playerUUID, username, provider);
				if (provider.matches("^[Dd][Ii][Rr]:.*")) skinImage = getDIR(playerUUID, username, provider);
				// Return Skin Image if found, otherwise check the next provider
				if (skinImage.isPresent()) return skinImage;
			}
			return skinImage;
		};

		// Implement custom skin provider
		blueMapAPI.getPlugin().setSkinProvider(customSkinProvider);
	};

	// =================
	// SUPPORT FUNCTIONS
	// =================

	// Validate string is useful
	private final Boolean validString(String input) {
		return (input != null && !input.isEmpty() && !input.isBlank());
	}

	// Get skin from URL and return image
	private final Optional<BufferedImage> getImageFromURL(URL skinURL) {
		BufferedImage skinImage;
		if (skinURL == null) return Optional.empty();
		logger.info("Downloading Skin: " + skinURL);
		try {
			InputStream skinStream = skinURL.openStream();
			skinImage = ImageIO.read(skinStream);
		} catch (IOException e) {
			e.printStackTrace();
			return Optional.empty();
		}
		return Optional.of(skinImage);
	}

	// Decode Properties String and extract skin URL from JSON Object
	private final URL getSkinURLFromEncodedProperties(String encodedProperties) {
		byte[] skinDataBytes = Base64.getDecoder().decode(encodedProperties);

		JSONObject skinData;

		try {
			skinData = (JSONObject) jsonParser.parse(new String(skinDataBytes, StandardCharsets.UTF_8));
		} catch (ParseException ex) {
			ex.printStackTrace();
			return null;
		}

		try {
			return new URL((String) ((JSONObject) ((JSONObject) skinData.get("textures")).get("SKIN")).get("url"));
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
		}

		return null;
	}

	// Get the skinProperty URL from SkinsRestorer
	private final URL getSkinsRestorerSkinUrl(UUID playerUUID) {
		final PlayerStorage playerStorage = skinsRestorerAPI.getPlayerStorage();
		
			final Optional<SkinProperty> playerSkinPropertyOptional = playerStorage.getSkinOfPlayer(playerUUID);
		
		if (!playerSkinPropertyOptional.isPresent()) {
			return null;
		}
		
		final SkinProperty playerSkinProperty = playerSkinPropertyOptional.get();

		final String skinPropertyEncoded = playerSkinProperty.getValue();

		return getSkinURLFromEncodedProperties(skinPropertyEncoded);
	};

	 // Get JSON data from API
	private final Optional<JSONObject> getJSONFromURL(URL RequestURL) {
		Optional<JSONObject> urlJSON = Optional.empty();
			try {
			InputStream urlStream = RequestURL.openStream();
			String urlJSONString = IOUtils.toString(urlStream, StandardCharsets.UTF_8);
			if (!validString(urlJSONString)) return Optional.empty();
			urlJSON = Optional.ofNullable((JSONObject) jsonParser.parse(urlJSONString));
		} catch (ParseException | IOException e) {
			e.printStackTrace();
		}

		return urlJSON;
	}

	private final URL getMojangSkinURL(String playerUUIDString) throws MalformedURLException {
		// Request profile information based on the UUID
		String formattedUUID = playerUUIDString.replace("-", "");
		URL requestURL = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + formattedUUID + "?unsigned=false");
		Optional<JSONObject> mojangJSONOptional = getJSONFromURL(requestURL);
		// Check if the result was present
		if (!mojangJSONOptional.isPresent()) return null;
		// Extract Encoded MojangValues
		JSONObject mojangJSON = mojangJSONOptional.get();
		String mojangEncodedString = ((String) ((JSONObject) ((JSONArray) mojangJSON.get("properties")).get(0)).get("value"));
		// Check if the result was present or errored
		if (!validString(mojangEncodedString)) return null;
		// Decode and extract the skinURL
		URL mojangSkinURL = getSkinURLFromEncodedProperties(mojangEncodedString);
		return mojangSkinURL;
	}

	// String Replacer
	private final String replacePlaceholders(UUID playerUUID, String username, String rawString) {
		String replaceString = rawString;
		// Perform Replacements
		replaceString = replaceString.replace("{UUID}", playerUUID.toString());
		replaceString = replaceString.replace("{UUID-}", playerUUID.toString().replaceAll("-",""));
		replaceString = replaceString.replace("{UUID_}", playerUUID.toString().replaceAll("-","_"));
		replaceString = replaceString.replace("{USERNAME}", username);
		replaceString = replaceString.replace("{USERNAME_LC}", username.toLowerCase());
		replaceString = replaceString.replace("{USERNAME_UC}", username.toUpperCase());
		return replaceString;
	};

	// ==================
	// PROVIDER FUNCTIONS
	// ==================

	// SkinsRestorer Provider
	private final Optional<BufferedImage> getSkinsRestorer(UUID playerUUID, String username) {
		logger.info("Checking SkinsRestorer");
		return getImageFromURL(getSkinsRestorerSkinUrl(playerUUID));
	};

	// Mojang Provider
	private final Optional<BufferedImage> getMojang(UUID playerUUID, String username) throws MalformedURLException {
		// Cache the playerUUID as a String
		String effectivePlayerUUIDString = playerUUID.toString();

		if (!serverOnlineMode) {
			logger.info("Chekcing Mojang (Offline Mode).");
			// Request Official Mojang UUID for the supplied username
			URL requestURL = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
			Optional<JSONObject> mojangJSONOptional = getJSONFromURL(requestURL);
			// Check if the result was present
			if (!mojangJSONOptional.isPresent()) return Optional.empty();
			// Extract Encoded MojangValues
			JSONObject mojangJSON = mojangJSONOptional.get();
			effectivePlayerUUIDString = (((String) mojangJSON.get("id")));
			// Verify if there was a result
			if (!validString(effectivePlayerUUIDString)) return Optional.empty();
		} else {
			logger.info("Checking Mojang");
		}

		// Request the UUID for the player
		Optional<BufferedImage> mojangSkinImage = getImageFromURL(getMojangSkinURL(effectivePlayerUUIDString)); // Always Returns Optional

		return mojangSkinImage;
	};

	// URL Provider
	private final Optional<BufferedImage> getURL(UUID playerUUID, String username, String rawURL) {
		logger.info("Checking URL: " + rawURL);

		// Replace placeholders
		String replacedURL = replacePlaceholders(playerUUID, username, rawURL);
		// Process into URL and get image
		try {
			return getImageFromURL(new URL(replacedURL)); //Always Returns Optional
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
		}
		// URL Failed
		return Optional.empty();
	};

	// Directory Provider
	private final Optional<BufferedImage> getDIR(UUID playerUUID, String username, String rawDIR) {
		logger.info("Checking DIR: " + rawDIR);
		// Cleanup URL
		String cleanDIR = rawDIR.replaceAll("^[Dd][Ii][Rr]:", "");
		String replacedDIR = replacePlaceholders(playerUUID, username, cleanDIR);
		// Use directory folder if not absolute
		if (!replacedDIR.matches("^/")) replacedDIR = this.getDataFolder().toString() + "/" + replacedDIR;
		logger.info("Using: " + replacedDIR);
		// Verify file exists
		File imgFile = new File(replacedDIR);
		if(!imgFile.exists() || imgFile.isDirectory()) return Optional.empty();
		// Get the image as BufferImage
		try {
			BufferedImage imgLoaded = ImageIO.read(imgFile);
			return Optional.ofNullable(imgLoaded);
		} catch (IOException e) { 
			e.printStackTrace(); 
		}
		return Optional.empty();
	};
}