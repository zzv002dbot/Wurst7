/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;
import com.google.gson.JsonParseException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public class WurstTranslator implements ResourceManagerReloadListener
{
	private final WurstClient wurst = WurstClient.INSTANCE;
	private ClientLanguage mcEnglish;
	
	private Map<String, String> currentLangStrings = Map.of();
	private Map<String, String> englishOnlyStrings = Map.of();
	private Map<String, String> currentLangRawStrings = Map.of();
	private Map<String, String> englishOnlyRawStrings = Map.of();
	private List<RawTemplate> currentLangRawTemplates = List.of();
	private List<RawTemplate> englishOnlyRawTemplates = List.of();
	
	@Override
	public void onResourceManagerReload(ResourceManager manager)
	{
		mcEnglish = ClientLanguage.loadFrom(manager,
			Lists.newArrayList("en_us"), false);
		
		HashMap<String, String> currentLangStrings = new HashMap<>();
		loadTranslations(manager, getCurrentLangCodes(),
			currentLangStrings::put);
		this.currentLangStrings =
			Collections.unmodifiableMap(currentLangStrings);
		
		HashMap<String, String> englishOnlyStrings = new HashMap<>();
		loadTranslations(manager, List.of("en_us"), englishOnlyStrings::put);
		this.englishOnlyStrings =
			Collections.unmodifiableMap(englishOnlyStrings);
		
		HashMap<String, String> currentLangRawStrings = new HashMap<>();
		loadRawTranslations(manager, getCurrentLangCodes(),
			currentLangRawStrings::put);
		this.currentLangRawStrings =
			Collections.unmodifiableMap(currentLangRawStrings);
		this.currentLangRawTemplates = buildRawTemplates(currentLangRawStrings);
		
		HashMap<String, String> englishOnlyRawStrings = new HashMap<>();
		loadRawTranslations(manager, List.of("en_us"),
			englishOnlyRawStrings::put);
		this.englishOnlyRawStrings =
			Collections.unmodifiableMap(englishOnlyRawStrings);
		this.englishOnlyRawTemplates = buildRawTemplates(englishOnlyRawStrings);
	}
	
	/**
	 * Translates the given key with the given args into the current language,
	 * or into English if the "Force English" setting is enabled. Both Wurst and
	 * vanilla translations are supported.
	 */
	public String translate(String key, Object... args)
	{
		// Forced English
		if(isForcedEnglish())
			return translateEnglish(key, args);
		
		// Wurst translation
		String string = currentLangStrings.get(key);
		if(string != null)
			try
			{
				return String.format(string, args);
				
			}catch(IllegalFormatException e)
			{
				return key;
			}
		
		// Vanilla translation
		return translateMc(key, args);
	}
	
	/**
	 * Translates the given key with the given args into English, regardless of
	 * the current language. Both Wurst and vanilla translations are supported.
	 */
	public String translateEnglish(String key, Object... args)
	{
		String string = englishOnlyStrings.get(key);
		if(string == null)
			string = mcEnglish.getOrDefault(key);
		
		try
		{
			return String.format(string, args);
			
		}catch(IllegalFormatException e)
		{
			return key;
		}
	}
	
	/**
	 * Translates the given key with the given args into the current language,
	 * or into English if the "Force English" setting is enabled, using only
	 * Minecraft's own translations.
	 *
	 * @apiNote This method differs from
	 *          {@link I18n#get(String, Object...)} in that it does not
	 *          return "Format error" if the key contains a percent sign.
	 */
	public String translateMc(String key, Object... args)
	{
		if(I18n.exists(key))
			return I18n.get(key, args);
		
		return key;
	}
	
	/**
	 * Translates the given key with the given args into English, regardless of
	 * the current language, using only Minecraft's own translations.
	 *
	 * @apiNote This method differs from
	 *          {@link I18n#get(String, Object...)} in that it does not
	 *          return "Format error" if the key contains a percent sign.
	 */
	public String translateMcEnglish(String key, Object... args)
	{
		try
		{
			return String.format(mcEnglish.getOrDefault(key), args);
			
		}catch(IllegalFormatException e)
		{
			return key;
		}
	}
	
	/**
	 * Translates an English literal string into the current language (or into
	 * English if "Force English" is enabled). If no translation exists, returns
	 * the original text unchanged.
	 */
	public String translateRaw(String text, Object... args)
	{
		if(args.length == 0 && text.contains("\n"))
		{
			String[] lines = text.split("\n", -1);
			for(int i = 0; i < lines.length; i++)
				lines[i] = translateRaw(lines[i]);
			
			return String.join("\n", lines);
		}
		
		Map<String, String> map;
		List<RawTemplate> templates;
		
		if(isForcedEnglish())
		{
			map = englishOnlyRawStrings;
			templates = englishOnlyRawTemplates;
			
		}else
		{
			map = currentLangRawStrings;
			templates = currentLangRawTemplates;
		}
		
		String translated = map.get(text);
		if(translated == null)
			translated = tryTemplateMatch(text, templates);
		if(translated == null)
			translated = text;
		
		try
		{
			return String.format(translated, args);
			
		}catch(IllegalFormatException e)
		{
			return translated;
		}
	}
	
	public boolean isForcedEnglish()
	{
		return wurst.getOtfs().translationsOtf.getForceEnglish().isChecked();
	}
	
	/**
	 * Returns a translation storage for Minecraft's English strings, regardless
	 * of the current language. Does not include any of Wurst's translations.
	 */
	public ClientLanguage getMcEnglish()
	{
		return mcEnglish;
	}
	
	public Map<String, String> getMinecraftsCurrentLanguage()
	{
		return currentLangStrings;
	}
	
	public Map<String, String> getWurstsCurrentLanguage()
	{
		return isForcedEnglish() ? englishOnlyStrings
			: getMinecraftsCurrentLanguage();
	}
	
	private ArrayList<String> getCurrentLangCodes()
	{
		// Weird bug: Some users have their language set to "en_US" instead of
		// "en_us" for some reason. Last seen in 1.21.
		String mainLangCode = Minecraft.getInstance().getLanguageManager()
			.getSelected().toLowerCase();
		
		ArrayList<String> langCodes = new ArrayList<>();
		langCodes.add("en_us");
		if(!"en_us".equals(mainLangCode))
			langCodes.add(mainLangCode);
		
		return langCodes;
	}
	
	private void loadTranslations(ResourceManager manager,
		Iterable<String> langCodes, BiConsumer<String, String> entryConsumer)
	{
		for(String langCode : langCodes)
		{
			String langFilePath = "translations/" + langCode + ".json";
			Identifier langId =
				Identifier.fromNamespaceAndPath("wurst", langFilePath);
			
			// IMPORTANT: Exceptions thrown by Language.loadFromJson() must
			// be caught to prevent mod detection vulnerabilities using
			// intentionally corrupted resource packs.
			for(Resource resource : manager.getResourceStack(langId))
				try(InputStream stream = resource.open())
				{
					if(isBuiltInWurstResourcePack(resource))
						Language.loadFromJson(stream, entryConsumer);
					
				}catch(IOException | JsonParseException e)
				{
					System.out.println(
						"Failed to load Wurst translations for " + langCode);
					e.printStackTrace();
					
				}catch(Exception e)
				{
					System.out.println(
						"Unexpected exception while loading Wurst translations for "
							+ langCode);
					e.printStackTrace();
				}
		}
	}
	
	private void loadRawTranslations(ResourceManager manager,
		Iterable<String> langCodes, BiConsumer<String, String> entryConsumer)
	{
		for(String langCode : langCodes)
		{
			String langFilePath = "raw_translations/" + langCode + ".json";
			Identifier langId =
				Identifier.fromNamespaceAndPath("wurst", langFilePath);
			
			// IMPORTANT: Exceptions thrown by Language.loadFromJson() must
			// be caught to prevent mod detection vulnerabilities using
			// intentionally corrupted resource packs.
			for(Resource resource : manager.getResourceStack(langId))
				try(InputStream stream = resource.open())
				{
					if(isBuiltInWurstResourcePack(resource))
						Language.loadFromJson(stream, entryConsumer);
					
				}catch(IOException | JsonParseException e)
				{
					System.out
						.println("Failed to load Wurst raw translations for "
							+ langCode);
					e.printStackTrace();
					
				}catch(Exception e)
				{
					System.out.println(
						"Unexpected exception while loading Wurst raw translations for "
							+ langCode);
					e.printStackTrace();
				}
		}
	}
	
	private List<RawTemplate> buildRawTemplates(Map<String, String> entries)
	{
		ArrayList<RawTemplate> templates = new ArrayList<>();
		
		entries.forEach((source, target) -> {
			Pattern sourceRegex = buildSourceTemplateRegex(source);
			if(sourceRegex != null)
				templates.add(new RawTemplate(source, target, sourceRegex));
		});
		
		templates.sort((a, b) -> Integer.compare(b.sourceTemplate.length(),
			a.sourceTemplate.length()));
		return Collections.unmodifiableList(templates);
	}
	
	private Pattern buildSourceTemplateRegex(String source)
	{
		StringBuilder regex = new StringBuilder("^");
		boolean hasPlaceholder = false;
		
		for(int i = 0; i < source.length(); i++)
		{
			char c = source.charAt(i);
			if(c != '%')
			{
				regex.append(Pattern.quote(String.valueOf(c)));
				continue;
			}
			
			if(i + 1 >= source.length())
				return null;
			
			if(source.charAt(i + 1) == '%')
			{
				regex.append("%");
				i++;
				continue;
			}
			
			int placeholderEnd = i + 1;
			while(placeholderEnd < source.length()
				&& !Character.isLetter(source.charAt(placeholderEnd)))
			{
				if(Character.isWhitespace(source.charAt(placeholderEnd)))
					return null;
				
				placeholderEnd++;
			}
			
			if(placeholderEnd >= source.length())
				return null;
			
			char type = source.charAt(placeholderEnd);
			if(type == 'n')
			{
				regex.append("\\R");
				i = placeholderEnd;
				continue;
			}
			
			hasPlaceholder = true;
			regex.append("(.+?)");
			i = placeholderEnd;
		}
		
		if(!hasPlaceholder)
			return null;
		
		regex.append("$");
		return Pattern.compile(regex.toString(), Pattern.DOTALL);
	}
	
	private String tryTemplateMatch(String input, List<RawTemplate> templates)
	{
		for(RawTemplate template : templates)
		{
			Matcher matcher = template.sourceRegex.matcher(input);
			if(!matcher.matches())
				continue;
			
			Object[] args = new Object[matcher.groupCount()];
			for(int i = 0; i < args.length; i++)
				args[i] = matcher.group(i + 1);
			
			try
			{
				return String.format(template.targetTemplate, args);
				
			}catch(IllegalFormatException e)
			{
				return template.targetTemplate;
			}
		}
		
		return null;
	}
	
	private static final class RawTemplate
	{
		private final String sourceTemplate;
		private final String targetTemplate;
		private final Pattern sourceRegex;
		
		private RawTemplate(String sourceTemplate, String targetTemplate,
			Pattern sourceRegex)
		{
			this.sourceTemplate = sourceTemplate;
			this.targetTemplate = targetTemplate;
			this.sourceRegex = sourceRegex;
		}
	}
	
	/**
	 * Ensures that the given resource is from Wurst's built-in resource pack,
	 * or at least from another client-side mod pretending to be Wurst, as it
	 * should be impossible for server-provided resource packs to obtain a
	 * KnownPack of <code>fabric:wurst</code>.
	 *
	 * <p>
	 * ASSUME THEY CAN BYPASS THIS. CATCH EXCEPTIONS ANYWAY.
	 */
	private boolean isBuiltInWurstResourcePack(Resource resource)
	{
		KnownPack knownPack = Optional.ofNullable(resource)
			.flatMap(Resource::knownPackInfo).orElse(null);
		if(knownPack == null)
			return false;
			
		// Note: Namespace can be "fabric" or "vanilla" depending on
		// Fabric API version (changed in 0.139.3+1.21.11).
		return ("fabric".equals(knownPack.namespace())
			|| "vanilla".equals(knownPack.namespace()))
			&& "wurst".equals(knownPack.id());
	}
}
