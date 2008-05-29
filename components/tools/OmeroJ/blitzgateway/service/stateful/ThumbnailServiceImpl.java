/*
 * blitzgateway.service.stateful.ThumbnailServiceImpl 
 *
  *------------------------------------------------------------------------------
 *  Copyright (C) 2006-2007 University of Dundee. All rights reserved.
 *
 *
 * 	This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */
package blitzgateway.service.stateful;



//Java imports
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//Third-party libraries

//Application-internal dependencies
import omero.RInt;

import org.openmicroscopy.shoola.env.data.DSAccessException;
import org.openmicroscopy.shoola.env.data.DSOutOfServiceException;

import blitzgateway.service.gateway.GatewayFactory;
import blitzgateway.service.gateway.ThumbnailGateway;

/** 
 * 
 *
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * 	<a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author	Donald MacDonald &nbsp;&nbsp;&nbsp;&nbsp;
 * 	<a href="mailto:donald@lifesci.dundee.ac.uk">donald@lifesci.dundee.ac.uk</a>
 * @version 3.0
 * <small>
 * (<b>Internal version:</b> $Revision: $Date: $)
 * </small>
 * @since OME3.0
 */
public class ThumbnailServiceImpl
	implements ThumbnailService
{	
	/** The gateway factory to create make connection, create and access 
	 *  services .
	 */
	private GatewayFactory 	gatewayFactory;

	/** 
	 * Map of the pixelsId and the gateway, this is used to store the created
	 * renderingEngineGateways. 
	 */
	private Map<Long, ThumbnailGateway> gatewayMap;
	
	/** batch service for batch thumbnail calls. */
	private ThumbnailGateway batchService;
	
	/**
	 * Create the ImageService passing the gateway.
	 * @param gatewayFactory To generate new instances of the 
	 * RenderingEngineGateway.
	 * @throws DSAccessException 
	 * @throws DSOutOfServiceException 
	 */
	public ThumbnailServiceImpl(GatewayFactory gatewayFactory) 
		throws DSOutOfServiceException, DSAccessException 
	{
		this.gatewayFactory = gatewayFactory;
		gatewayMap = new HashMap<Long, ThumbnailGateway>();
		batchService = gatewayFactory.getThumbnailGateway(-1L);
	}

	/**
	 * Get the gateway for pixels from the map, if it does not exist create it
	 * and add it to the map.
	 * @param pixelsId see above.
	 * @return see above.
	 * @throws DSOutOfServiceException
	 * @throws DSAccessException
	 */
	private synchronized ThumbnailGateway getGateway(Long pixelsId) throws DSOutOfServiceException, DSAccessException
	{
		synchronized(gatewayMap)
		{
			if(gatewayMap.containsKey(pixelsId))
			{
				return gatewayMap.get(pixelsId);
			}
			else
			{
				ThumbnailGateway gateway = gatewayFactory.getThumbnailGateway(pixelsId);
				gatewayMap.put(pixelsId, gateway);
				return gateway;
			}
		}
	}
	
	/**
	 * Does the gateway map contain the gateway for pixelsId.
	 * @param pixelsId see above.
	 * @return see above.
	 */
	public synchronized boolean containsGateway(long pixelsId)
	{
		synchronized(gatewayMap)
		{
			return gatewayMap.containsKey(pixelsId);
		}
	}
	
	/**
	 * Close the gateway for pixels = pixelsId
	 * @param pixelsId see above.
	 * @return true if the gateway was closed.
	 * @throws DSOutOfServiceException
	 * @throws DSAccessException
	 */
	public synchronized boolean closeGateway(long pixelsId) throws DSOutOfServiceException, DSAccessException
	{
		synchronized(gatewayMap)
		{
			if(containsGateway(pixelsId))
			{
				gatewayMap.remove(pixelsId);
				return true;
			}
			else
				return false;
		}
	}

	/* (non-Javadoc)
	 * @see blitzgateway.service.stateful.ThumbnailService#getThumbnail(long, omero.RInt, omero.RInt)
	 */
	public byte[] getThumbnail(long pixelsId, RInt sizeX, RInt sizeY)
			throws DSOutOfServiceException, DSAccessException
	{
		ThumbnailGateway gateway = getGateway(pixelsId);
		synchronized(gateway)
		{
			return gateway.getThumbnail(sizeX, sizeY);
		}
	}

	/* (non-Javadoc)
	 * @see blitzgateway.service.stateful.ThumbnailService#getThumbnailByLongestSide(long, omero.RInt)
	 */
	public byte[] getThumbnailByLongestSide(long pixelsId, RInt size)
			throws DSOutOfServiceException, DSAccessException
	{
		ThumbnailGateway gateway = getGateway(pixelsId);
		synchronized(gateway)
		{
			return gateway.getThumbnailByLongestSide(size);
		}
	}

	/* (non-Javadoc)
	 * @see blitzgateway.service.stateful.ThumbnailService#getThumbnailByLongestSideSet(omero.RInt, java.util.List)
	 */
	public Map<Long, byte[]> getThumbnailByLongestSideSet(RInt size,
			List<Long> pixelsIds) throws DSOutOfServiceException,
			DSAccessException
	{
		synchronized(batchService)
		{
			return batchService.getThumbnailByLongestSideSet(size, pixelsIds);
		}
	}

	/* (non-Javadoc)
	 * @see blitzgateway.service.stateful.ThumbnailService#getThumbnailSet(omero.RInt, omero.RInt, java.util.List)
	 */
	public Map<Long, byte[]> getThumbnailSet(RInt sizeX, RInt sizeY,
			List<Long> pixelsIds) throws DSOutOfServiceException,
			DSAccessException
	{
		synchronized(batchService)
		{
			return batchService.getThumbnailSet(sizeX, sizeY, pixelsIds);
		}
	}

	/* (non-Javadoc)
	 * @see blitzgateway.service.stateful.ThumbnailService#setRenderingDefId(long, long)
	 */
	public void setRenderingDefId(long pixelsId, long renderingDefId)
			throws DSOutOfServiceException, DSAccessException
	{
		ThumbnailGateway gateway = getGateway(pixelsId);
		synchronized(gateway)
		{
			gateway.setRenderingDefId(renderingDefId);
		}
	}

}


