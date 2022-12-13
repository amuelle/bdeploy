import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-bd-editor-diff',
  templateUrl: './bd-editor-diff.component.html',
})
export class BdEditorDiffComponent implements OnInit, OnDestroy {
  private globalMonaco;
  private monaco;
  private subscription: Subscription;

  @Input() originalContent: string;
  @Input() modifiedContent: string;
  @Input() path = '';
  @Output() modifiedContentChange = new EventEmitter<string>();

  /* template */ editorOptions = {
    theme: this.themeService.isDarkTheme() ? 'vs-dark' : 'vs',
    language: 'plaintext',
    automaticLayout: true,
  };

  /* template */ inited$ = new BehaviorSubject<boolean>(false);

  constructor(
    private themeService: ThemeService,
    private cd: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.subscription = this.themeService.getThemeSubject().subscribe(() => {
      if (this.globalMonaco) {
        this.globalMonaco.editor.setTheme(
          this.themeService.isDarkTheme() ? 'vs-dark' : 'vs'
        );
      }
    });
  }

  public update() {
    this.monaco.getModifiedEditor().getModel().setValue(this.modifiedContent);
  }

  onMonacoInit(monaco) {
    this.monaco = monaco;
    this.globalMonaco = window['monaco'];

    // wait for init to complete, otherwise we leak models.
    setTimeout(() => this.initMonaco(), 0);

    // async init of monaco editor when the model changes - this is only for testing purposes.
    setTimeout(() => {
      this.inited$.next(true);
      this.cd.detectChanges();
    }, 1000);
  }

  initMonaco() {
    this.globalMonaco.editor.getModels().forEach((m) => m.dispose());
    const model = {
      original: this.globalMonaco.editor.createModel(
        this.originalContent,
        undefined
      ),
      modified: this.globalMonaco.editor.createModel(
        this.modifiedContent,
        undefined,
        this.globalMonaco.Uri.parse(this.path)
      ),
    };
    this.monaco.setModel(model);
    this.monaco.getModifiedEditor().onDidChangeModelContent(() => {
      this.modifiedContentChange.emit(
        this.monaco.getModifiedEditor().getValue()
      );
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
