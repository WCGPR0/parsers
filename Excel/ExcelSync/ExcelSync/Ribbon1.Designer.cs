namespace ExcelSync
{
    partial class Ribbon1 : Microsoft.Office.Tools.Ribbon.RibbonBase
    {
        /// <summary>
        /// Required designer variable.
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        public Ribbon1()
            : base(Globals.Factory.GetRibbonFactory())
        {
            InitializeComponent();
        }

        /// <summary> 
        /// Clean up any resources being used.
        /// </summary>
        /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Component Designer generated code

        /// <summary>
        /// Required method for Designer support - do not modify
        /// the contents of this method with the code editor.
        /// </summary>
        private void InitializeComponent()
        {
            this.Sync = this.Factory.CreateRibbonTab();
            this.group1 = this.Factory.CreateRibbonGroup();
            this.buttonToggleConnect = this.Factory.CreateRibbonToggleButton();
            this.editBoxChannel = this.Factory.CreateRibbonEditBox();
            this.LabelStatus = this.Factory.CreateRibbonLabel();
            this.group2 = this.Factory.CreateRibbonGroup();
            this.label1 = this.Factory.CreateRibbonLabel();
            this.label2 = this.Factory.CreateRibbonLabel();
            this.label3 = this.Factory.CreateRibbonLabel();
            this.Sync.SuspendLayout();
            this.group1.SuspendLayout();
            this.group2.SuspendLayout();
            // 
            // Sync
            // 
            this.Sync.Groups.Add(this.group1);
            this.Sync.Groups.Add(this.group2);
            this.Sync.Label = "ExcelSync";
            this.Sync.Name = "Sync";
            // 
            // group1
            // 
            this.group1.Items.Add(this.buttonToggleConnect);
            this.group1.Items.Add(this.editBoxChannel);
            this.group1.Items.Add(this.LabelStatus);
            this.group1.Label = "Real time Sync";
            this.group1.Name = "group1";
            // 
            // buttonToggleConnect
            // 
            this.buttonToggleConnect.Label = "Connect";
            this.buttonToggleConnect.Name = "buttonToggleConnect";
            this.buttonToggleConnect.OfficeImageId = "ListSynchronize";
            this.buttonToggleConnect.ShowImage = true;
            this.buttonToggleConnect.Click += new Microsoft.Office.Tools.Ribbon.RibbonControlEventHandler(this.buttonToggleConnect_Click);
            // 
            // editBoxChannel
            // 
            this.editBoxChannel.Label = "Channel:";
            this.editBoxChannel.Name = "editBoxChannel";
            this.editBoxChannel.Text = null;
            // 
            // LabelStatus
            // 
            this.LabelStatus.Label = "Not Connected";
            this.LabelStatus.Name = "LabelStatus";
            // 
            // group2
            // 
            this.group2.Items.Add(this.label1);
            this.group2.Items.Add(this.label2);
            this.group2.Items.Add(this.label3);
            this.group2.Label = "Last Change";
            this.group2.Name = "group2";
            // 
            // label1
            // 
            this.label1.Label = "                                     ";
            this.label1.Name = "label1";
            // 
            // label2
            // 
            this.label2.Label = "        ";
            this.label2.Name = "label2";
            // 
            // label3
            // 
            this.label3.Label = "             ";
            this.label3.Name = "label3";
            // 
            // Ribbon1
            // 
            this.Name = "Ribbon1";
            this.RibbonType = "Microsoft.Excel.Workbook";
            this.Tabs.Add(this.Sync);
            this.Load += new Microsoft.Office.Tools.Ribbon.RibbonUIEventHandler(this.Ribbon1_Load);
            this.Sync.ResumeLayout(false);
            this.Sync.PerformLayout();
            this.group1.ResumeLayout(false);
            this.group1.PerformLayout();
            this.group2.ResumeLayout(false);
            this.group2.PerformLayout();

        }

        #endregion

        internal Microsoft.Office.Tools.Ribbon.RibbonTab Sync;
        internal Microsoft.Office.Tools.Ribbon.RibbonGroup group1;
        internal Microsoft.Office.Tools.Ribbon.RibbonEditBox editBoxChannel;
        public Microsoft.Office.Tools.Ribbon.RibbonLabel LabelStatus;
        internal Microsoft.Office.Tools.Ribbon.RibbonToggleButton buttonToggleConnect;
        internal Microsoft.Office.Tools.Ribbon.RibbonGroup group2;
        public Microsoft.Office.Tools.Ribbon.RibbonLabel label1;
        public Microsoft.Office.Tools.Ribbon.RibbonLabel label2;
        public Microsoft.Office.Tools.Ribbon.RibbonLabel label3;
    }

    partial class ThisRibbonCollection
    {
        internal Ribbon1 Ribbon1
        {
            get { return this.GetRibbon<Ribbon1>(); }
        }
    }
}
