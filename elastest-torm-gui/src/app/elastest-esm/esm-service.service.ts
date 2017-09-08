import { EsmServiceModel } from './esm-service.model';
import { ConfigurationService } from '../config/configuration-service.service';
import { Injectable } from '@angular/core';
import { Http, Response } from '@angular/http';

@Injectable()
export class EsmService {

  constructor(private http: Http, private configurationService: ConfigurationService) { }

  getElastestESMServices(){
    let url = this.configurationService.configModel.hostApi + '/esm/services';
    return this.http.get(url)
      .map((response) =>  this.transformIntoEsmServiceModel(response)
    );
  }

  transformIntoEsmServiceModel(response: Response ){
    let res =  response.json();
    let retrivedServices: EsmServiceModel[] = [];

    for(let service of res){
      retrivedServices.push(new EsmServiceModel(service.id, service.name, false));
    }
    return retrivedServices;    
  }

}