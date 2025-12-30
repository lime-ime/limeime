/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.data;

/**
 * Represents a mapping record for UI layer operations.
 * 
 * <p>This is an alias for {@link Mapping} used in the Setup/Manage IM UI layer
 * where "Record" terminology is more appropriate for database maintenance
 * operations.
 * 
 * <p>All fields and methods are inherited from {@link Mapping}.
 * 
 * @author LimeIME Team
 * @see Mapping
 */
public class Record extends Mapping {

	/** Empty constructor */
	public Record() {
		super();
	}

	/**
	 * Copy constructor.
	 * @param mapping The mapping to copy
	 */
	public Record(Mapping mapping) {
		super(mapping);
	}
}
