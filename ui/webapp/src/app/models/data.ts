/**
 * A type hint for the colum which may (mostly in card mode) have influence on where and how information is rendered.
 */
export enum BdDataColumnTypeHint {
  /* The type, an uppercased text at the very top, should descripe the kind of object the card represents */
  TYPE,
  /* The title of the displayed object */
  TITLE,
  /* The description of the displayed object. Shown below the title */
  DESCRIPTION,
  /* May appear multiple times: A 'detail' about the displayed object, rendered using the 'icon' and 'data' in a table */
  DETAILS,
  /* May appear multiple times: An action which can be performed on the displayed object, will trigger the columns 'action' */
  ACTIONS,
  /* Column's 'data' contains a URL to an image, which is to be shown as image */
  AVATAR,
  /* A footer text (single line, ellipsiszed) shown below the details and actions. */
  FOOTER,
}

/** Determines in which mode a column should be shown */
export enum BdDataColumnDisplay {
  TABLE,
  CARD,
  BOTH,
}

/**
 * Defines a column of the data table.
 */
export interface BdDataColumn<T> {
  /** internal ID of the column, used to keep track of columns */
  id: string;

  /** The name of the column, usually only displayed in table mode or in filter selection */
  name: string;

  /** Receives a row, extracts the data to display for this column. */
  data: (row: T) => any;

  /** The description of the column, usually displayed as a tooltip somewhere */
  description?: string;

  /** The columns width in CSS style (e.g. '36px', '8%', etc.) */
  width?: string;

  /** Show column in table mode when this media query is resolved */
  showWhen?: string;

  /** If set, render the contents of the column as button, which will trigger the given action when clicked. The data passed is the row object. */
  action?: (row: T) => void;

  /** A callback to determine an icon for the cell - action or detail item. The data passed is the row object. */
  icon?: (row: T) => string;

  /** A callback to determine additional CSS classes to apply to the item, which may be either plain text, a button, etc. The data passed is the row object. */
  classes?: (row: T) => string[];

  /** A hint which determines where to place column contents on a card. */
  hint?: BdDataColumnTypeHint;

  /** In which mode the colum should be displayed, both if unset */
  display?: BdDataColumnDisplay;
}

export interface BdDataGrouping<T> {
  /** determines the name of the group this grouping would put the record in. */
  group: (row: T) => string;

  /** determines wether the given group should be shown with the current grouping settings */
  show: (group: string) => boolean;

  /** provides sorting for the selected groups */
  sort: (a: string, b: string) => number;
}

/** The default search which uses case insensitive search in all fields of the record. */
export function bdDataDefaultSearch<T>(search: string, records: T[]): T[] {
  if (!search) {
    return records;
  }

  const lowerSearch = search.toLowerCase();
  return records.filter((r) => {
    const value = createSearchString(r);
    return value.indexOf(lowerSearch) !== -1;
  });
}

/** Takes any object and produces a "searchable" string by joining all field values into a space separated string */
function createSearchString(val: any): string {
  let result = '';
  for (const field of Object.values(val)) {
    if (typeof field === 'object') {
      result += createSearchString(field);
    } else {
      result += String(field).toLowerCase() + ' ';
    }
  }
  return result;
}