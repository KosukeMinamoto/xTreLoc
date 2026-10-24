package com.treloc.xtreloc.app.gui.util;

import com.treloc.xtreloc.util.TimeFormatConverter;
import java.util.Comparator;
import java.util.List;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 * Header sort that cycles ascending → descending → the table's original order.
 */
public final class ToggleAscRowSorter<M extends TableModel> extends TableRowSorter<M> {

    private static final Comparator<Object> NUMERIC = Comparator.comparing(ToggleAscRowSorter::toDouble,
        Comparator.nullsLast(Double::compareTo));
    private static final Comparator<Object> TEXT = Comparator.comparing(ToggleAscRowSorter::toText,
        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    private static final Comparator<Object> TIME = Comparator.comparing(ToggleAscRowSorter::toEpoch,
        Comparator.nullsLast(Long::compareTo));

    public ToggleAscRowSorter(M model) {
        super(model);
        setSortsOnUpdates(true);
    }

    @Override
    public void toggleSortOrder(int column) {
        if (column < 0 || !isSortable(column)) {
            return;
        }
        List<? extends SortKey> keys = getSortKeys();
        if (!keys.isEmpty() && keys.get(0).getColumn() == column) {
            if (keys.get(0).getSortOrder() == SortOrder.ASCENDING) {
                setSortKeys(List.of(new SortKey(column, SortOrder.DESCENDING)));
                return;
            }
            setSortKeys(null);
            return;
        }
        setSortKeys(List.of(new SortKey(column, SortOrder.ASCENDING)));
    }

    public ToggleAscRowSorter<M> numericColumns(int... columns) {
        return comparators(NUMERIC, columns);
    }

    public ToggleAscRowSorter<M> textColumns(int... columns) {
        return comparators(TEXT, columns);
    }

    public ToggleAscRowSorter<M> timeColumns(int... columns) {
        return comparators(TIME, columns);
    }

    private ToggleAscRowSorter<M> comparators(Comparator<Object> comparator, int... columns) {
        for (int column : columns) {
            setComparator(column, comparator);
        }
        return this;
    }

    public static int modelRow(JTable table, int viewRow) {
        if (table == null || viewRow < 0) {
            return -1;
        }
        RowSorter<?> sorter = table.getRowSorter();
        return sorter == null ? viewRow : table.convertRowIndexToModel(viewRow);
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Long toEpoch(Object value) {
        if (value == null) {
            return null;
        }
        return TimeFormatConverter.toEpochMillis(value.toString());
    }
}
