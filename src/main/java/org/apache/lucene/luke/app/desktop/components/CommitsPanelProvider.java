package org.apache.lucene.luke.app.desktop.components;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.luke.app.DirectoryHandler;
import org.apache.lucene.luke.app.DirectoryObserver;
import org.apache.lucene.luke.app.IndexHandler;
import org.apache.lucene.luke.app.IndexObserver;
import org.apache.lucene.luke.app.LukeState;
import org.apache.lucene.luke.app.desktop.util.TableUtil;
import org.apache.lucene.luke.app.desktop.util.MessageUtils;
import org.apache.lucene.luke.models.commits.Commit;
import org.apache.lucene.luke.models.commits.Commits;
import org.apache.lucene.luke.models.commits.CommitsFactory;
import org.apache.lucene.luke.models.commits.File;
import org.apache.lucene.luke.models.commits.Segment;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommitsPanelProvider implements Provider<JPanel> {

  private final CommitsFactory commitsFactory;

  private final JComboBox<Long> commitGenCombo = new JComboBox<>();

  private final JLabel deletedLbl = new JLabel();

  private final JLabel segCntLbl = new JLabel();

  private final JTextArea userDataTA = new JTextArea();

  private final JTable filesTable = new JTable();

  private final JTable segmentsTable = new JTable();

  private final JRadioButton diagRB = new JRadioButton();

  private final JRadioButton attrRB = new JRadioButton();

  private final JRadioButton codecRB = new JRadioButton();

  private final ButtonGroup rbGroup = new ButtonGroup();

  private final JList<String> segDetailList = new JList<>();

  private ListenerFunctions listeners = new ListenerFunctions();

  private Commits commitsModel;

  class ListenerFunctions {

    void selectGeneration(ActionEvent e) {
      diagRB.setEnabled(false);
      attrRB.setEnabled(false);
      codecRB.setEnabled(false);
      segDetailList.setModel(new DefaultListModel<>());

      long commitGen = (long) commitGenCombo.getSelectedItem();
      commitsModel.getCommit(commitGen).ifPresent(commit -> {
        deletedLbl.setText(String.valueOf(commit.isDeleted()));
        segCntLbl.setText(String.valueOf(commit.getSegCount()));
        userDataTA.setText(commit.getUserData());
      });

      filesTable.setModel(new FileTableModel(commitsModel.getFiles(commitGen)));
      filesTable.setShowGrid(true);
      filesTable.getColumnModel().getColumn(FileTableModel.Column.FILENAME.getIndex()).setPreferredWidth(200);

      segmentsTable.setModel(new SegmentTableModel(commitsModel.getSegments(commitGen)));
      segmentsTable.setShowGrid(true);
      segmentsTable.getColumnModel().getColumn(SegmentTableModel.Column.NAME.getIndex()).setPreferredWidth(60);
      segmentsTable.getColumnModel().getColumn(SegmentTableModel.Column.MAXDOCS.getIndex()).setPreferredWidth(60);
      segmentsTable.getColumnModel().getColumn(SegmentTableModel.Column.DELS.getIndex()).setPreferredWidth(60);
      segmentsTable.getColumnModel().getColumn(SegmentTableModel.Column.DELGEN.getIndex()).setPreferredWidth(60);
      segmentsTable.getColumnModel().getColumn(SegmentTableModel.Column.VERSION.getIndex()).setPreferredWidth(80);
      segmentsTable.getColumnModel().getColumn(SegmentTableModel.Column.CODEC.getIndex()).setPreferredWidth(100);
    }

    void showSegmentDetails(MouseEvent e) {
      int selectedRow = segmentsTable.getSelectedRow();
      if (commitGenCombo.getSelectedItem() == null ||
          selectedRow < 0 || selectedRow >= segmentsTable.getRowCount()) {
        return;
      }

      diagRB.setEnabled(true);
      attrRB.setEnabled(true);
      codecRB.setEnabled(true);

      long commitGen = (long) commitGenCombo.getSelectedItem();
      String segName = (String)segmentsTable.getValueAt(selectedRow, SegmentTableModel.Column.NAME.getIndex());
      ActionCommand command = ActionCommand.valueOf(rbGroup.getSelection().getActionCommand());

      final DefaultListModel<String> detailsModel = new DefaultListModel<>();
      switch (command) {
        case DIAGNOSTICS:
          commitsModel.getSegmentDiagnostics(commitGen, segName).entrySet().stream()
              .map(entry -> entry.getKey() + " = " + entry.getValue())
              .forEach(detailsModel::addElement);
          break;
        case ATTRIBUTES:
          commitsModel.getSegmentAttributes(commitGen, segName).entrySet().stream()
              .map(entry -> entry.getKey() + " = " + entry.getValue())
              .forEach(detailsModel::addElement);
          break;
        case CODEC:
          commitsModel.getSegmentCodec(commitGen, segName).ifPresent(codec -> {
            Map<String, String> map = new HashMap<>();
            map.put("Codec name", codec.getName());
            map.put("Codec class name", codec.getClass().getName());
            map.put("Compound format", codec.compoundFormat().getClass().getName());
            map.put("DocValues format", codec.docValuesFormat().getClass().getName());
            map.put("FieldInfos format", codec.fieldInfosFormat().getClass().getName());
            map.put("LiveDocs format", codec.liveDocsFormat().getClass().getName());
            map.put("Norms format", codec.normsFormat().getClass().getName());
            map.put("Points format", codec.pointsFormat().getClass().getName());
            map.put("Postings format", codec.postingsFormat().getClass().getName());
            map.put("SegmentInfo format", codec.segmentInfoFormat().getClass().getName());
            map.put("StoredFields format", codec.storedFieldsFormat().getClass().getName());
            map.put("TermVectors format", codec.termVectorsFormat().getClass().getName());
            map.entrySet().stream()
                .map(entry -> entry.getKey() + " = " + entry.getValue()).forEach(detailsModel::addElement);
          });
          break;
      }
      segDetailList.setModel(detailsModel);

    }
  }

  class Observer implements IndexObserver, DirectoryObserver {

    @Override
    public void openDirectory(LukeState state) {
      commitsModel = commitsFactory.newInstance(state.getDirectory(), state.getIndexPath());
      populateCommitGenerations();
    }

    @Override
    public void closeDirectory() {
      close();
    }

    @Override
    public void openIndex(LukeState state) {
      if (state.hasDirectoryReader()) {
        DirectoryReader dr = (DirectoryReader) state.getIndexReader();
        commitsModel = commitsFactory.newInstance(dr, state.getIndexPath());
        populateCommitGenerations();
      }
    }

    @Override
    public void closeIndex() {
      close();
    }

    private void populateCommitGenerations() {
      DefaultComboBoxModel<Long> segGenList = new DefaultComboBoxModel<>();
      for (Commit commit : commitsModel.listCommits()) {
        segGenList.addElement(commit.getGeneration());
      }
      commitGenCombo.setModel(segGenList);

      if (segGenList.getSize() > 0) {
        commitGenCombo.setSelectedIndex(0);
      }
    }

    private void close() {
      commitsModel = null;

      commitGenCombo.setModel(new DefaultComboBoxModel<>());
      deletedLbl.setText("");
      segCntLbl.setText("");
      userDataTA.setText("");
      TableUtil.setupTable(filesTable, ListSelectionModel.SINGLE_SELECTION, new FileTableModel(), null, 200);
      TableUtil.setupTable(segmentsTable, ListSelectionModel.SINGLE_SELECTION, new SegmentTableModel(), null, 60, 60, 60, 60, 100, 150);
      diagRB.setEnabled(false);
      attrRB.setEnabled(false);
      codecRB.setEnabled(false);
      segDetailList.setModel(new DefaultListModel<>());
    }
  }

  @Inject
  public CommitsPanelProvider(CommitsFactory commitsFactory,
                              IndexHandler indexHandler,
                              DirectoryHandler directoryHandler) {
    this.commitsFactory = commitsFactory;

    indexHandler.addObserver(new Observer());
    directoryHandler.addObserver(new Observer());
  }

  @Override
  public JPanel get() {
    JPanel panel = new JPanel(new GridLayout(1, 1));
    panel.setBorder(BorderFactory.createLineBorder(Color.gray));

    JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createUpperPanel(), createLowerPanel());
    splitPane.setBorder(BorderFactory.createEmptyBorder());
    splitPane.setDividerLocation(120);
    panel.add(splitPane);

    return panel;
  }

  private JPanel createUpperPanel() {
    JPanel panel = new JPanel(new BorderLayout(20, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEADING));
    left.add(new JLabel(MessageUtils.getLocalizedMessage("commits.label.select_gen")));
    commitGenCombo.addActionListener(listeners::selectGeneration);
    left.add(commitGenCombo);
    panel.add(left, BorderLayout.LINE_START);

    JPanel right = new JPanel(new GridBagLayout());
    GridBagConstraints c1 = new GridBagConstraints();
    c1.ipadx = 5;
    c1.ipady = 5;

    c1.gridx = 0;
    c1.gridy = 0;
    c1.weightx = 0.2;
    c1.anchor = GridBagConstraints.EAST;
    right.add(new JLabel(MessageUtils.getLocalizedMessage("commits.label.deleted")), c1);

    c1.gridx = 1;
    c1.gridy = 0;
    c1.weightx = 0.5;
    c1.anchor = GridBagConstraints.WEST;
    right.add(deletedLbl, c1);

    c1.gridx = 0;
    c1.gridy = 1;
    c1.weightx = 0.2;
    c1.anchor = GridBagConstraints.EAST;
    right.add(new JLabel(MessageUtils.getLocalizedMessage("commits.label.segcount")), c1);

    c1.gridx = 1;
    c1.gridy = 1;
    c1.weightx = 0.5;
    c1.anchor = GridBagConstraints.WEST;
    right.add(segCntLbl, c1);

    c1.gridx = 0;
    c1.gridy = 2;
    c1.weightx = 0.2;
    c1.anchor = GridBagConstraints.EAST;
    right.add(new JLabel(MessageUtils.getLocalizedMessage("commits.label.userdata")), c1);

    userDataTA.setRows(3);
    userDataTA.setColumns(30);
    userDataTA.setLineWrap(true);
    userDataTA.setWrapStyleWord(true);
    userDataTA.setEditable(false);
    JScrollPane userDataScroll = new JScrollPane(userDataTA, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    c1.gridx = 1;
    c1.gridy = 2;
    c1.weightx = 0.5;
    c1.anchor = GridBagConstraints.WEST;
    right.add(userDataScroll, c1);

    panel.add(right, BorderLayout.CENTER);

    return panel;
  }

  private JPanel createLowerPanel() {
    JPanel panel = new JPanel(new GridLayout(1, 1));
    panel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createFilesPanel(), createSegmentsPanel());
    splitPane.setBorder(BorderFactory.createEmptyBorder());
    splitPane.setDividerLocation(300);
    panel.add(splitPane);
    return panel;
  }

  private JPanel createFilesPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

    JPanel header = new JPanel(new FlowLayout(FlowLayout.LEADING));
    header.add(new JLabel(MessageUtils.getLocalizedMessage("commits.label.files")));
    panel.add(header, BorderLayout.PAGE_START);

    TableUtil.setupTable(filesTable, ListSelectionModel.SINGLE_SELECTION, new FileTableModel(), null, 200);
    panel.add(new JScrollPane(filesTable), BorderLayout.CENTER);

    return panel;
  }

  private JPanel createSegmentsPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

    JPanel segments = new JPanel(new FlowLayout(FlowLayout.LEADING));
    segments.add(new JLabel(MessageUtils.getLocalizedMessage("commits.label.segments")));
    panel.add(segments);

    TableUtil.setupTable(segmentsTable, ListSelectionModel.SINGLE_SELECTION, new SegmentTableModel(), new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        listeners.showSegmentDetails(e);
      }
    }, 60, 60, 60, 60, 100, 150);
    panel.add(new JScrollPane(segmentsTable));

    JPanel segDetails = new JPanel(new FlowLayout(FlowLayout.LEADING));
    segDetails.add(new JLabel(MessageUtils.getLocalizedMessage("commits.label.segdetails")));
    panel.add(segDetails);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING));

    diagRB.setText("Diagnostics");
    diagRB.setActionCommand(ActionCommand.DIAGNOSTICS.name());
    diagRB.setSelected(true);
    diagRB.setEnabled(false);
    diagRB.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        listeners.showSegmentDetails(e);
      }
    });
    buttons.add(diagRB);

    attrRB.setText("Attributes");
    attrRB.setActionCommand(ActionCommand.ATTRIBUTES.name());
    attrRB.setSelected(false);
    attrRB.setEnabled(false);
    attrRB.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        listeners.showSegmentDetails(e);
      }
    });
    buttons.add(attrRB);

    codecRB.setText("Codec");
    codecRB.setActionCommand(ActionCommand.CODEC.name());
    codecRB.setSelected(false);
    codecRB.setEnabled(false);
    codecRB.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        listeners.showSegmentDetails(e);
      }
    });
    buttons.add(codecRB);

    rbGroup.add(diagRB);
    rbGroup.add(attrRB);
    rbGroup.add(codecRB);

    panel.add(buttons);

    segDetailList.setVisibleRowCount(10);
    panel.add(new JScrollPane(segDetailList));

    return panel;
  }

  enum ActionCommand {
    DIAGNOSTICS, ATTRIBUTES, CODEC;
  }

}

class FileTableModel extends AbstractTableModel {

  enum Column implements TableColumnInfo {

    FILENAME("Filename", 0, String.class),
    SIZE("Size", 1, String.class);

    private String colName;
    private int index;
    private Class<?> type;

    Column(String colName, int index, Class<?> type) {
      this.colName = colName;
      this.index = index;
      this.type = type;
    }

    @Override
    public String getColName() {
      return colName;
    }

    @Override
    public int getIndex() {
      return index;
    }

    @Override
    public Class<?> getType() {
      return type;
    }
  }

  private static final Map<Integer, Column> columnMap = TableUtil.columnMap(Column.values());

  private final String[] colNames = TableUtil.columnNames(Column.values());

  private final Object[][] data;

  FileTableModel() {
    this.data = new Object[0][colNames.length];
  }

  FileTableModel(List<File> files) {
    this.data = new Object[files.size()][colNames.length];
    for (int i = 0; i < files.size(); i++) {
      File file = files.get(i);
      data[i][Column.FILENAME.getIndex()] = file.getFileName();
      data[i][Column.SIZE.getIndex()] = file.getDisplaySize();
    }
  }

  @Override
  public int getRowCount() {
    return data.length;
  }

  @Override
  public int getColumnCount() {
    return colNames.length;
  }

  @Override
  public String getColumnName(int colIndex) {
    if (columnMap.containsKey(colIndex)) {
      return columnMap.get(colIndex).colName;
    }
    return "";
  }

  @Override
  public Class<?> getColumnClass(int colIndex) {
    if (columnMap.containsKey(colIndex)) {
      return columnMap.get(colIndex).type;
    }
    return Object.class;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return data[rowIndex][columnIndex];
  }
}

class SegmentTableModel extends AbstractTableModel {

  enum Column implements TableColumnInfo {

    NAME("Name", 0, String.class),
    MAXDOCS("Max docs", 1, Integer.class),
    DELS("Dels", 2, Integer.class),
    DELGEN("Del gen", 3, Long.class),
    VERSION("Lucene ver.", 4, String.class),
    CODEC("Codec", 5, String.class),
    SIZE("Size", 6, String.class);

    private String colName;
    private int index;
    private Class<?> type;

    Column(String colName, int index, Class<?> type) {
      this.colName = colName;
      this.index = index;
      this.type = type;
    }

    @Override
    public String getColName() {
      return colName;
    }

    @Override
    public int getIndex() {
      return index;
    }

    @Override
    public Class<?> getType() {
      return type;
    }
  }

  private static final Map<Integer, Column> columnMap = TableUtil.columnMap(Column.values());

  private final String[] colNames = TableUtil.columnNames(Column.values());

  private final Object[][] data;

  SegmentTableModel() {
    this.data = new Object[0][colNames.length];
  }

  SegmentTableModel(List<Segment> segments) {
    this.data = new Object[segments.size()][colNames.length];
    for (int i = 0; i < segments.size(); i++) {
      Segment segment = segments.get(i);
      data[i][Column.NAME.getIndex()] = segment.getName();
      data[i][Column.MAXDOCS.getIndex()] = segment.getMaxDoc();
      data[i][Column.DELS.getIndex()] = segment.getDelCount();
      data[i][Column.DELGEN.getIndex()] = segment.getDelGen();
      data[i][Column.VERSION.getIndex()] = segment.getLuceneVer();
      data[i][Column.CODEC.getIndex()] = segment.getCodecName();
      data[i][Column.SIZE.getIndex()] = segment.getDisplaySize();
    }
  }

  @Override
  public int getRowCount() {
    return data.length;
  }

  @Override
  public int getColumnCount() {
    return colNames.length;
  }

  @Override
  public String getColumnName(int colIndex) {
    if (columnMap.containsKey(colIndex)) {
      return columnMap.get(colIndex).colName;
    }
    return "";
  }

  @Override
  public Class<?> getColumnClass(int colIndex) {
    if (columnMap.containsKey(colIndex)) {
      return columnMap.get(colIndex).type;
    }
    return Object.class;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return data[rowIndex][columnIndex];
  }

}