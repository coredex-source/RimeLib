package me.ancientri.rimelib.config

import me.ancientri.rimelib.util.FabricLoader
import me.ancientri.symbols.config.ConfigClass
import org.slf4j.Logger
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.*

/**
 * A generic abstract class for managing configurations in a mod.
 * This manager is responsible for:
 * - Loading the configuration from a file.
 * - Saving the configuration to a file.
 * - Providing a way to read and modify the configuration.
 *
 * This manager is designed to work with immutable configuration objects, meaning that the config object held by this manager should not be modified directly.
 * Instead, modifications should be made through a [ConfigBuilder][B] instance, which can then be used to create a new config object.
 *
 * It's recommended to use [ConfigClass] annotation on the config class to generate the builder automatically, but you can also implement the [ConfigBuilder][B] interface manually if needed.
 *
 * @param C The type of the config object.
 * @param B The type of the config builder.
 * @param F The type of the intermediary object type used for serialization, such as a JSON object or a map.
 */
abstract class ConfigManager<C : Any, B : ConfigBuilder<C>, F : Any> {
	abstract val logger: Logger

	/**
	 * The path to the configuration file. This should be an absolute path, as some child of [FabricLoader.configDir][net.fabricmc.loader.api.FabricLoader.getConfigDir].
	 */
	abstract val configPath: Path

	/**
	 * The current configuration object.
	 * This should be an immutable object that represents the current state of the configuration.
	 * This doesn't mean the object itself doesn't hold any immutable data, but rather that care should be taken to not modify the data directly.
	 *
	 * For modifications, use the [modifyConfig] method which will return a new config instance.
	 * Note that this doesn't save the config to the file automatically, so you should call [saveConfig] after modifying the config to persist changes.
	 * @see modifyConfig
	 */
	var config: C
		protected set

	/**
	 * The config object created with default values.
	 * This will be used to initialize the config when the config file does not exist or is invalid.
	 */
	abstract val default: C

	/**
	 * Convenience property to get the relative path of the config file from the config directory.
	 */
	val relativePath: Path
		get() = FabricLoader.configDir.relativize(configPath)

	init {
		config = if (configPath.notExists()) {
			logger.info("Config file {} does not exist, creating with default values.", relativePath)
			saveConfig(default)
			default
		} else when (val loadedConfig = loadConfig()) {
			null -> {
				logger.warn("Config file {} is invalid or could not be loaded, using default values.", relativePath)
				default
			}

			else -> {
				logger.info("Loaded config file {}.", relativePath)
				loadedConfig
			}
		}
	}

	/**
	 * Creates a new [ConfigBuilder][B] instance.
	 */
	abstract fun builder(config: C): B

	/**
	 * Writes the provided data to the output stream.
	 * @param stream The output stream to write to. The caller will close this stream.
	 * @param data The data to write to the stream, which is an intermediary representation of the config.
	 */
	abstract fun writeToStream(stream: OutputStream, data: F)

	/**
	 * Reads the data from the input stream.
	 * @param stream The input stream to read from. The caller will close this stream.
	 * @return The intermediary representation of the config, which can be used to decode into the actual config object.
	 */
	abstract fun readFromStream(stream: InputStream): F

	/**
	 * Encodes the provided config object into an intermediary representation.
	 * @param config The config object to encode.
	 * @return The intermediary representation of the config, which can be written to a file.
	 */
	abstract fun encode(config: C): F?

	/**
	 * Decodes the provided intermediary data into a config object.
	 * @param data The intermediary representation of the config.
	 * @return The decoded config object, or null if the data is invalid or cannot be decoded.
	 */
	abstract fun decode(data: F): C?

	/**
	 * Modifies the current config using the provided builder function and returns the updated config.
	 *
	 * Note that this function **does not** save the changes to the config file.
	 * @see updateConfig
	 */
	fun modifyConfig(builder: B.() -> Unit): C {
		config = this.builder(config).apply(builder).build()
		return config
	}

	/**
	 * Modifies the current config using the provided builder function and saves the changes to the config file.
	 * @param builder A lambda function that takes a [B] (config builder) and modifies it.
	 */
	fun updateConfig(builder: B.() -> Unit): C {
		saveConfig(modifyConfig(builder))
		return config
	}

	/**
	 * Saves the current configuration to the file.
	 * This method should be called after modifying the config to persist changes.
	 */
	fun saveConfig(config: C = this.config) = when (val encoded = encode(config)) {
		null -> {
			logger.error("Failed to encode config.")
		}

		else -> {
			configPath.createParentDirectories()
			configPath.outputStream().use { writer ->
				writeToStream(writer, encoded)
			}
		}
	}

	/**
	 * Loads the configuration from the file.
	 * @return The loaded configuration object, or null if the file could not be loaded or is invalid.
	 */
	fun loadConfig(): C? = when {
		configPath.exists() -> configPath.inputStream().use { reader ->
			decode(readFromStream(reader))
		}

		else -> null
	}
}