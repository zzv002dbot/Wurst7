/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.other_features;

import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.other_feature.OtherFeature;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

@DontBlock
@SearchTags({"privacy", "data", "tracking", "snooper", "spyware"})
public final class NoTelemetryOtf extends OtherFeature
{
	private final CheckboxSetting disableTelemetry =
		new CheckboxSetting("Disable telemetry", true);
	
	public NoTelemetryOtf()
	{
		super("NoTelemetry",
			"Disables the \"required\" telemetry that Mojang introduced in 22w46a. Turns out it's not so required after all.");
		addSetting(disableTelemetry);
	}
	
	@Override
	public boolean isEnabled()
	{
		return disableTelemetry.isChecked();
	}
	
	@Override
	public String getPrimaryAction()
	{
		return isEnabled() ? ChatUtils.tr("Re-enable Telemetry")
			: ChatUtils.tr("Disable Telemetry");
	}
	
	@Override
	public void doPrimaryAction()
	{
		disableTelemetry.setChecked(!disableTelemetry.isChecked());
	}
	
	// See TelemetrySenderMixin
}
