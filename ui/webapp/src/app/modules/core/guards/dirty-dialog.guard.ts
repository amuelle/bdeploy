import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanDeactivate, Router, RouterStateSnapshot } from '@angular/router';
import { Observable, of } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
import { BdDialogMessageAction } from '../components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from '../components/bd-dialog/bd-dialog.component';
import { LoggingService } from '../services/logging.service';
import { NavAreasService } from '../services/nav-areas.service';

enum DirtyActionType {
  CANCEL = 'CANCEL',
  DISCARD = 'DISCARD',
  SAVE = 'SAVE',
}

const actCancel: BdDialogMessageAction<DirtyActionType> = {
  name: 'CANCEL',
  result: DirtyActionType.CANCEL,
  confirm: false,
};

const actDiscard: BdDialogMessageAction<DirtyActionType> = {
  name: 'DISCARD',
  result: DirtyActionType.DISCARD,
  confirm: false,
};

const actSave: BdDialogMessageAction<DirtyActionType> = {
  name: 'SAVE',
  result: DirtyActionType.SAVE,
  confirm: false,
};

/**
 * A dialog which may be dirty. Navigation if dirty will trigger a confirmation.
 *
 * Note: There are three requirements for proper dirty handling:
 *  1) Implement the DirtyableDialog interface.
 *  2) Register the component in the constructur with NavAreasService#registerDirtyable.
 *  3) Add the DirtyDialogGuard to canDeactivate of the components route.
 */
export interface DirtyableDialog {
  /** The dialog with dirty handling, used to issue confirmations */
  dialog: BdDialogComponent;

  /** Determines whether the dialog is dirty. */
  isDirty(): boolean;

  /** Saves the current state - may NOT perform ANY navigation! */
  doSave(): Observable<any>;
}

@Injectable({
  providedIn: 'root',
})
export class DirtyDialogGuard implements CanDeactivate<DirtyableDialog> {
  private log = this.logging.getLogger('DirtyDialogGuard');

  constructor(private logging: LoggingService, private areas: NavAreasService, private router: Router) {}

  canDeactivate(
    component: DirtyableDialog,
    currentRoute: ActivatedRouteSnapshot,
    currentState: RouterStateSnapshot,
    nextState?: RouterStateSnapshot
  ): Observable<boolean> {
    const ignore = this.router.getCurrentNavigation()?.extras?.state?.ignoreDirtyGuard;
    if (!!ignore) {
      return of(true); // forced navigation.
    }

    // If there is an open *and* dirty panel, special handling is required to keep popups
    // in visible areas (maximized panel).
    if (this.areas.hasDirtyPanel() && this.areas.getDirtyableType(component) !== 'panel') {
      // 1. confirm on panel - otherwise reject
      // 2. hide panel
      // 3. confirm on primary - otherwise reject
      // 4. acutally close panel (discard data)

      return this.confirm(this.areas.getDirtyable('panel')).pipe(
        switchMap((r) => {
          if (r === DirtyActionType.CANCEL) {
            return of(false);
          } else {
            // hide it *directly*, not via navigation. a nested navigation would cancel the current one.
            // to be able to see the prompt when the panel was maximised, we need to hide the panel.
            this.areas.panelVisible$.next(false);

            let panelSave = of(true);
            if (r === DirtyActionType.SAVE) {
              // need to save!
              panelSave = this.areas.getDirtyable('panel').doSave();
            }

            // if the primary dialog is not dirtyable, continue.
            if (!this.areas.getDirtyableType(component)) {
              this.areas.forcePanelClose$.next(true);
              return panelSave.pipe(switchMap((_) => of(true))); // disregard the result, can be any.
            }

            // ask confirmation on the actual component (the primary one in this case).
            return panelSave.pipe(
              switchMap((_) =>
                this.confirm(component).pipe(
                  switchMap((x) => {
                    if (x !== DirtyActionType.CANCEL) {
                      // navigation accepted. since the panel is only hidden, we need to make sure it is completely closed
                      // once the navigation is done.
                      this.areas.forcePanelClose$.next(true);

                      let primarySave = of(true);
                      if (x === DirtyActionType.SAVE) {
                        primarySave = component.doSave();
                      }
                      return primarySave.pipe(switchMap((_) => of(true)));
                    } else {
                      // navigation is cancelled. we *instead* close the panel fully, since it is only hidden now.
                      this.areas.closePanel(true);
                      return of(false);
                    }
                  })
                )
              )
            );
          }
        })
      );
    }

    if (!!this.areas.getDirtyableType(component)) {
      if (!component.isDirty()) {
        return of(true);
      }

      return this.confirm(component).pipe(
        switchMap((x) => {
          if (x === DirtyActionType.CANCEL) {
            return of(false);
          }
          if (x === DirtyActionType.SAVE) {
            return component.doSave().pipe(switchMap((_) => of(true)));
          }
          return of(true);
        })
      );
    } else {
      return of(true);
    }
  }

  private confirm(component: DirtyableDialog) {
    return component.dialog
      .message({
        header: 'Unsaved Changes',
        message: 'The dialog contains unsaved changes. Leaving the dialog will discard unsaved changes.',
        icon: 'save',
        actions: [actCancel, actDiscard, actSave],
      })
      .pipe(
        tap((result) => {
          if (result === DirtyActionType.DISCARD) {
            this.log.info('User confirmed discarding pending changes.');
          }
        })
      );
  }
}