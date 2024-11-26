import { Component, OnInit, inject } from '@angular/core';
import { combineLatest, map } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { ManagedMasterDto, OperatingSystem } from 'src/app/models/gen.dtos';
import { BdDataSvgIconCellComponent } from 'src/app/modules/core/components/bd-data-svg-icon-cell/bd-data-svg-icon-cell.component';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ServerDetailsService } from '../../services/server-details.service';

export interface MinionRow {
  name: string;
  os: OperatingSystem;
  master: boolean;
  version: string;
}

const detailNameCol: BdDataColumn<MinionRow> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
};

const detailMasterCol: BdDataColumn<MinionRow> = {
  id: 'master',
  name: 'Master',
  data: (r) => (r.master ? 'Yes' : ''),
  width: '60px',
};

const detailVersionCol: BdDataColumn<MinionRow> = {
  id: 'version',
  name: 'Vers.',
  data: (r) => r.version,
  width: '60px',
};

const detailOsCol: BdDataColumn<MinionRow> = {
  id: 'os',
  name: 'OS',
  data: (r) => r.os,
  component: BdDataSvgIconCellComponent,
  width: '30px',
};

@Component({
  selector: 'app-server-nodes',
  templateUrl: './server-nodes.component.html',
  providers: [ServerDetailsService],
})
export class ServerNodesComponent implements OnInit {
  private readonly servers = inject(ServersService);
  private readonly serverDetails = inject(ServerDetailsService);

  protected columns = [detailNameCol, detailVersionCol, detailMasterCol, detailOsCol];
  protected minions: MinionRow[];
  protected server: ManagedMasterDto;

  protected loading$ = combineLatest([this.servers.loading$, this.serverDetails.loading$]).pipe(
    map(([a, b]) => a || b),
  );

  ngOnInit(): void {
    this.serverDetails.server$.subscribe((server) => {
      if (!server) {
        return;
      }
      this.server = server;
      this.minions = this.getMinionRecords(server);
    });
  }

  private getMinionRecords(server: ManagedMasterDto): MinionRow[] {
    return Object.keys(server.minions.minions).map((k) => {
      const dto = server.minions.minions[k];
      return {
        name: k,
        os: dto.os,
        master: dto.master,
        version: convert2String(dto.version),
      };
    });
  }
}