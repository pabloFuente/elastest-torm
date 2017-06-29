import { NgModule, Type } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BrowserModule, Title } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

import { CovalentHttpModule, IHttpInterceptor } from '@covalent/http';
import { CovalentHighlightModule } from '@covalent/highlight';
import { CovalentMarkdownModule } from '@covalent/markdown';

import { AppComponent } from './app.component';
import { RequestInterceptor } from '../config/interceptors/request.interceptor';

import { routedComponents, AppRoutingModule } from './app-routing.module';

import { SharedModule } from './shared/shared.module';
import { StompService } from 'ng2-stomp-service';
import { StompWSManager } from './elastest-etm/stomp-ws-manager.service';
import { TJobService } from './elastest-etm/tjob/tjob.service';
import { SutService } from './elastest-etm/sut/sut.service';
import { ProjectService } from './elastest-etm/project/project.service';
import { ElasticSearchService } from './elastest-log-manager/services/elasticSearch.service';
import { MdDatepickerModule, MdNativeDateModule, MdRadioModule } from '@angular/material';


const httpInterceptorProviders: Type<any>[] = [
  RequestInterceptor,
];

@NgModule({
  declarations: [
    AppComponent,
    routedComponents,
  ], // directives, components, and pipes owned by this NgModule
  imports: [
    AppRoutingModule,
    BrowserModule,
    BrowserAnimationsModule,
    SharedModule,
    MdDatepickerModule,
    MdNativeDateModule,
    MdRadioModule,
    BrowserModule,
    FormsModule,
    CovalentHttpModule.forRoot({
      interceptors: [{
        interceptor: RequestInterceptor, paths: ['**'],
      }],
    }),
    CovalentHighlightModule,
    CovalentMarkdownModule,
  ], // modules needed to run this module
  providers: [
    httpInterceptorProviders,
    Title,
    StompService,
    StompWSManager,
    SutService,
    TJobService,
    ProjectService,
    ElasticSearchService,
  ],
  entryComponents: [],
  bootstrap: [AppComponent],
})
export class AppModule { }
