/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.command;

import java.util.Objects;

import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.util.ChatUtils;

public abstract class Command extends Feature
{
	private final String name;
	private final String description;
	private final String[] syntax;
	private Category category;
	
	public Command(String name, String description, String... syntax)
	{
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		
		Objects.requireNonNull(syntax);
		this.syntax = syntax;
		
		if(name.contains(" "))
			throw new IllegalArgumentException(
				"Feature name must not contain spaces: " + name);
	}
	
	public abstract void call(String[] args) throws CmdException;
	
	@Override
	public final String getName()
	{
		return "." + name;
	}
	
	@Override
	public String getPrimaryAction()
	{
		return "";
	}
	
	@Override
	public final String getDescription()
	{
		String description = trRawLines(this.description);
		
		if(syntax.length > 0)
			description += "\n";
		
		for(String line : syntax)
			description += "\n" + trRaw("Syntax: %s", line);
		
		return description;
	}
	
	public final String[] getSyntax()
	{
		return syntax;
	}
	
	public final void printHelp()
	{
		for(String line : description.split("\n"))
			ChatUtils.message(line);
		
		for(String line : syntax)
			ChatUtils.message(trRaw("Syntax: %s", line));
	}

	protected final String trRaw(String text, Object... args)
	{
		if(WURST.getTranslator() == null)
			try
			{
				return String.format(text, args);
				
			}catch(Exception e)
			{
				return text;
			}
		
		return WURST.getTranslator().translateRaw(text, args);
	}
	
	private String trRawLines(String text)
	{
		String[] lines = text.split("\n", -1);
		for(int i = 0; i < lines.length; i++)
			lines[i] = trRaw(lines[i]);
		
		return String.join("\n", lines);
	}
	
	@Override
	public final Category getCategory()
	{
		return category;
	}
	
	protected final void setCategory(Category category)
	{
		this.category = category;
	}
}
