package org.apache.lucene.luke.app.desktop.components.fragments.search;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.apache.lucene.luke.app.desktop.components.ComponentOperatorRegistry;
import org.apache.lucene.luke.app.desktop.util.MessageUtils;
import org.apache.lucene.luke.models.search.Search;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class SortPaneProvider implements Provider<JScrollPane> {

  private static final String COMMAND_FIELD_COMBO1 = "fieldCombo1";

  private static final String COMMAND_FIELD_COMBO2 = "fieldCombo2";

  private final JComboBox<String> fieldCombo1 = new JComboBox<>();

  private final JComboBox<String> typeCombo1 = new JComboBox<>();

  private final JComboBox<String> orderCombo1 = new JComboBox<>(Order.names());

  private final JComboBox<String> fieldCombo2 = new JComboBox<>();

  private final JComboBox<String> typeCombo2 = new JComboBox<>();

  private final JComboBox<String> orderCombo2 = new JComboBox<>(Order.names());

  private final ListenerFunctions listeners = new ListenerFunctions();

  private Search searchModel;

  class SortTabOperatorImpl implements SortTabOperator {
    @Override
    public void setSearchModel(Search model) {
      searchModel = model;
    }

    @Override
    public void setSortableFields(Collection<String> sortableFields) {
      fieldCombo1.removeAllItems();
      fieldCombo2.removeAllItems();

      fieldCombo1.addItem("");
      fieldCombo2.addItem("");

      for (String field : sortableFields) {
        fieldCombo1.addItem(field);
        fieldCombo2.addItem(field);
      }
    }

    @Override
    public Sort getSort() {
      if (Strings.isNullOrEmpty((String)fieldCombo1.getSelectedItem()) && Strings.isNullOrEmpty((String)fieldCombo2.getSelectedItem())) {
        return null;
      }

      List<SortField> li = new ArrayList<>();
      if (!Strings.isNullOrEmpty((String)fieldCombo1.getSelectedItem())) {
        searchModel.getSortType((String)fieldCombo1.getSelectedItem(), (String)typeCombo1.getSelectedItem(), isReverse(orderCombo1)).ifPresent(li::add);
      }
      if (!Strings.isNullOrEmpty((String)fieldCombo2.getSelectedItem())) {
        searchModel.getSortType((String)fieldCombo2.getSelectedItem(), (String)typeCombo2.getSelectedItem(), isReverse(orderCombo2)).ifPresent(li::add);
      }
      return new Sort(li.toArray(new SortField[li.size()]));
    }

    private boolean isReverse(JComboBox<String> order) {
      return Order.valueOf((String)order.getSelectedItem()) == Order.DESC;
    }

  }

  class ListenerFunctions {

    void changeField(ActionEvent e) {
      if (e.getActionCommand().equalsIgnoreCase(COMMAND_FIELD_COMBO1)) {
        resetField(fieldCombo1, typeCombo1, orderCombo1);
      } else if (e.getActionCommand().equalsIgnoreCase(COMMAND_FIELD_COMBO2)) {
        resetField(fieldCombo2, typeCombo2, orderCombo2);
      }
    }

    private void resetField(JComboBox<String> fieldCombo, JComboBox<String> typeCombo, JComboBox<String> orderCombo) {
      typeCombo.removeAllItems();
      if (Strings.isNullOrEmpty((String)fieldCombo.getSelectedItem())) {
        typeCombo.addItem("");
        typeCombo.setEnabled(false);
        orderCombo.setEnabled(false);
      } else {
        List<SortField> sortFields = searchModel.guessSortTypes((String)fieldCombo.getSelectedItem());
        sortFields.stream()
            .map(sf -> {
              if (sf instanceof SortedNumericSortField) {
                return ((SortedNumericSortField) sf).getNumericType().name();
              } else {
                return sf.getType().name();
              }
            }).forEach(typeCombo::addItem);
        typeCombo.setEnabled(true);
        orderCombo.setEnabled(true);
      }
    }

    void clear(ActionEvent e) {
      fieldCombo1.setSelectedIndex(0);
      typeCombo1.removeAllItems();
      typeCombo1.setSelectedItem("");
      typeCombo1.setEnabled(false);
      orderCombo1.setSelectedIndex(0);
      orderCombo1.setEnabled(false);

      fieldCombo2.setSelectedIndex(0);
      typeCombo2.removeAllItems();
      typeCombo2.setSelectedItem("");
      typeCombo2.setEnabled(false);
      orderCombo2.setSelectedIndex(0);
      orderCombo2.setEnabled(false);
    }
  }

  @Inject
  public SortPaneProvider(ComponentOperatorRegistry operatorRegistry) {
    operatorRegistry.register(SortTabOperator.class, new SortTabOperatorImpl());
  }

  @Override
  public JScrollPane get() {
    JPanel panel = new JPanel(new GridLayout(1, 1));
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    panel.add(sortConfigs());

    return new JScrollPane(panel);
  }

  private JPanel sortConfigs() {
    JPanel panel = new JPanel(new GridLayout(5, 1));
    panel.setMaximumSize(new Dimension(500, 200));

    panel.add(new JLabel(MessageUtils.getLocalizedMessage("search_sort.label.primary")));

    JPanel primary = new JPanel(new FlowLayout(FlowLayout.LEADING));
    primary.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
    primary.add(new JLabel(MessageUtils.getLocalizedMessage("search_sort.label.field")));
    fieldCombo1.setPreferredSize(new Dimension(150, 30));
    fieldCombo1.setActionCommand(COMMAND_FIELD_COMBO1);
    fieldCombo1.addActionListener(listeners::changeField);
    primary.add(fieldCombo1);
    primary.add(new JLabel(MessageUtils.getLocalizedMessage("search_sort.label.type")));
    typeCombo1.setPreferredSize(new Dimension(130, 30));
    typeCombo1.addItem("");
    typeCombo1.setEnabled(false);
    primary.add(typeCombo1);
    primary.add(new JLabel(MessageUtils.getLocalizedMessage("search_sort.label.order")));
    orderCombo1.setPreferredSize(new Dimension(100, 30));
    orderCombo1.setEnabled(false);
    primary.add(orderCombo1);
    panel.add(primary);

    panel.add(new JLabel(MessageUtils.getLocalizedMessage("search_sort.label.secondary")));

    JPanel secondary = new JPanel(new FlowLayout(FlowLayout.LEADING));
    secondary.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
    secondary.add(new JLabel(MessageUtils.getLocalizedMessage("search_sort.label.field")));
    fieldCombo2.setPreferredSize(new Dimension(150, 30));
    fieldCombo2.setActionCommand(COMMAND_FIELD_COMBO2);
    fieldCombo2.addActionListener(listeners::changeField);
    secondary.add(fieldCombo2);
    secondary.add(new JLabel(MessageUtils.getLocalizedMessage("search_sort.label.type")));
    typeCombo2.setPreferredSize(new Dimension(130, 30));
    typeCombo2.addItem("");
    typeCombo2.setEnabled(false);
    secondary.add(typeCombo2);
    secondary.add(new JLabel(MessageUtils.getLocalizedMessage("search_sort.label.order")));
    orderCombo2.setPreferredSize(new Dimension(100, 30));
    orderCombo2.setEnabled(false);
    secondary.add(orderCombo2);
    panel.add(secondary);

    JPanel clear = new JPanel(new FlowLayout(FlowLayout.LEADING));
    JButton clearBtn = new JButton(MessageUtils.getLocalizedMessage("search_sort.button.clear"));
    clearBtn.addActionListener(listeners::clear);
    clear.add(clearBtn);
    panel.add(clear);

    return panel;
  }

  public interface SortTabOperator extends ComponentOperatorRegistry.ComponentOperator {
    void setSearchModel(Search model);
    void setSortableFields(Collection<String> sortableFields);
    Sort getSort();
  }

  enum Order {
    ASC, DESC;

    static String[] names() {
      return Arrays.stream(values()).map(Order::name).toArray(String[]::new);
    }
  }
}
